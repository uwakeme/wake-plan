package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.notification.CompletionNotifier
import com.wakeplan.app.notification.ScheduleRefresher
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 完成任务 — v0.6 从 TodayViewModel 抽出（专注页打卡与 Today 共用同一语义）
 *
 * 一条链做完（与 Today 手动完成的口径完全一致）：
 * 1. IN_PROGRESS 守门 → 写 COMPLETED + actualEnd + overrun
 * 2. 链式延期落库（persistCascade，幂等）+ 重排下游闹钟
 * 3. task_completed 渠道通知（偏好开着才推）
 *
 * @return null = 守门拒绝（不存在 / 非 IN_PROGRESS）
 */
@Singleton
class CompleteTaskUseCase @Inject constructor(
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskRepository: TaskRepository,
    private val cascadeDelayCalculator: CascadeDelayCalculator,
    private val scheduleRefresher: ScheduleRefresher,
    private val completionNotifier: CompletionNotifier,
    private val clock: Clock,
) {

    data class Result(
        /** 完成前的 IN_PROGRESS 快照（撤销用） */
        val previous: TaskCompletion,
        val completed: TaskCompletion,
    )

    suspend operator fun invoke(completionId: String): Result? {
        val c = taskCompletionRepository.getById(completionId) ?: return null
        if (c.status != TaskStatus.IN_PROGRESS) return null
        val now = LocalDateTime.now(clock)
        val overrun = Duration.between(c.plannedEnd, now).toMinutes().coerceAtLeast(0L).toInt()
        val completed = c.copy(
            status = TaskStatus.COMPLETED,
            actualEnd = now,
            overrunMinutes = overrun,
        )
        taskCompletionRepository.upsert(completed)
        persistCascade(c.scheduledDate)
        val taskTitle = taskRepository.getById(c.taskId)?.title.orEmpty()
        if (taskTitle.isNotEmpty()) {
            completionNotifier.notifyCompleted(completed, taskTitle)
        }
        return Result(previous = c, completed = completed)
    }

    /**
     * 把当天链式延期结果写回 DB（v0.4.1：overrun 真正传导到下游并重排闹钟）。
     * 计算器幂等，重复调用安全。
     */
    private suspend fun persistCascade(date: LocalDate) {
        val completions = taskCompletionRepository.observeByDate(date).first()
        if (completions.isEmpty()) return
        val tasks = taskRepository.observeAll().first()
        val adjusted = cascadeDelayCalculator(completions, tasks, date, LocalDateTime.now(clock))
        val originalById = completions.associateBy { it.id }
        var changed = false
        adjusted.forEach { adj ->
            val orig = originalById[adj.id] ?: return@forEach
            if (orig.plannedStart != adj.plannedStart ||
                orig.plannedEnd != adj.plannedEnd ||
                orig.affectedTasks != adj.affectedTasks
            ) {
                taskCompletionRepository.upsert(adj)
                changed = true
            }
        }
        if (changed) scheduleRefresher.refresh(date)
    }
}
