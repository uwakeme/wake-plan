package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.data.repository.mapper.toEntity
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.repository.newId
import com.wakeplan.app.domain.usecase.TaskWindowCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskWindowCalculator: TaskWindowCalculator,
    private val clock: Clock,
) : TaskRepository {

    override fun observeByPlan(planId: String): Flow<List<Task>> =
        taskDao.observeByPlan(planId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Task>> =
        taskDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Task? =
        taskDao.findById(id)?.toDomain()

    override suspend fun create(
        planId: String,
        title: String,
        startMinutes: Int,
        endMinutes: Int,
        repeatRule: RepeatRule,
        customDays: Set<DayOfWeek>,
        isLocked: Boolean,
        note: String?,
    ): Task {
        val nextOrder = (taskDao.maxOrderInPlan(planId) ?: -1) + 1
        val task = Task(
            id = newId(),
            planId = planId,
            title = title,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            repeatRule = repeatRule,
            customDays = if (repeatRule == RepeatRule.CUSTOM) customDays else emptySet(),
            isLocked = isLocked,
            orderInPlan = nextOrder,
            note = note,
        )
        taskDao.insert(task.toEntity())
        return task
    }

    override suspend fun update(task: Task) {
        taskDao.update(task.toEntity())
        // 同步刷新该 task 未来 30 天 PENDING 状态 completion 的时间窗口
        // 已开始 / 已完成的 completion 保持不变（用户在跑的任务不应被打乱）
        syncPendingCompletionTimes(task)
    }

    private suspend fun syncPendingCompletionTimes(task: Task) {
        val today = LocalDate.now(clock)
        // 30 天够覆盖一个月的所有相关 completion
        val horizon = today.plusDays(30)
        val pending = taskCompletionRepository
            .getByDateRange(today, horizon.plusDays(1))
            .filter { it.taskId == task.id }

        for (completion in pending) {
            // 只修正时间窗口异常（plannedEnd 跨过次日中午的数据错误场景）；
            // COMPLETED / IN_PROGRESS 的 status / actualStart / actualEnd 保留不动
            val window = taskWindowCalculator(task, completion.scheduledDate) ?: continue
            val plannedDuration = java.time.Duration.between(window.plannedStart, window.plannedEnd).toMinutes()
            val storedDuration = java.time.Duration.between(completion.plannedStart, completion.plannedEnd).toMinutes()
            val drift = kotlin.math.abs(plannedDuration - storedDuration)
            // 仅当 stored 时长与 task 计划时长偏差超过 5 分钟时修正（防止无谓 IO）
            if (drift > 5) {
                taskCompletionRepository.upsert(
                    completion.copy(
                        plannedStart = window.plannedStart,
                        plannedEnd = window.plannedEnd,
                    )
                )
            }
        }
    }

    override suspend fun delete(id: String) {
        // 外键 CASCADE 会自动删除 task_completions；这里仅删 task
        taskDao.deleteById(id)
    }

    override suspend fun reorder(planId: String, orderedTaskIds: List<String>) {
        taskDao.reorder(planId, orderedTaskIds)
    }
}