package com.wakeplan.app.notification

import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.usecase.ScheduleManager
import com.wakeplan.app.domain.usecase.TaskCompletionInstantiator
import com.wakeplan.app.domain.usecase.TaskWindowCalculator
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * 当天调度刷新 — v0.4 从 TodayViewModel.ensureTodayCompletions 抽出
 *
 * 「补当天 PENDING 实例 + 同步任务窗口漂移 + 重排 starting 闹钟」一条链，两个入口共用：
 * - TodayViewModel：打开 App / 切回当天
 * - BootReceiver：系统重启 / 时间或时区变更后，AlarmManager 闹钟不存活，要静默重排
 */
@Singleton
class ScheduleRefresher @Inject constructor(
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskCompletionInstantiator: TaskCompletionInstantiator,
    private val taskWindowCalculator: TaskWindowCalculator,
    private val scheduleManager: ScheduleManager,
    private val startingAlarmScheduler: StartingAlarmScheduler,
    private val userPreferences: UserPreferences,
    private val clock: Clock,
) {

    /**
     * 刷新 [date]（一般是今天）的 completion 与 starting 闹钟
     */
    suspend fun refresh(date: LocalDate = LocalDate.now(clock)) {
        val plans = planRepository.observeAll().first()
        val tasks = taskRepository.observeAll().first()
        val existing = taskCompletionRepository.observeByDate(date).first()
        val existingTaskIds = existing.map { it.taskId }.toSet()
        val existingByTaskId = existing.associateBy { it.taskId }

        // 归档计划不铺当天实例、不参与调度（v0.4）
        plans.filter { it.isActive && !it.archived }.forEach { plan ->
            val planTasks = tasks.filter { it.planId == plan.id }
            val pending = taskCompletionInstantiator(
                plan = plan,
                tasks = planTasks,
                date = date,
            ).filter { it.taskId !in existingTaskIds }
            pending.forEach { taskCompletionRepository.upsert(it) }

            // 同步已存在 completion 的时间窗口（修复 picker bug 留下的跨日数据）
            // 不限制 PENDING：COMPLETED/IN_PROGRESS 也修（只改 planned 时间，不动 status/actualStart/actualEnd）
            planTasks.forEach { task ->
                val old = existingByTaskId[task.id] ?: return@forEach
                val window = taskWindowCalculator(task, date) ?: return@forEach
                val plannedDuration = Duration.between(window.plannedStart, window.plannedEnd).toMinutes()
                val storedDuration = Duration.between(old.plannedStart, old.plannedEnd).toMinutes()
                val drift = abs(plannedDuration - storedDuration)
                if (drift > 5) {
                    taskCompletionRepository.upsert(
                        old.copy(
                            plannedStart = window.plannedStart,
                            plannedEnd = window.plannedEnd,
                        )
                    )
                }
            }
        }
        val todaysCompletions = taskCompletionRepository.observeByDate(date).first()
        val now = LocalDateTime.now(clock)
        val leadMinutes = userPreferences.taskStartingLeadMinutes.first()
        val startingEnabled = userPreferences.taskStartingEnabled.first()
        val plan = scheduleManager(
            completions = todaysCompletions,
            tasks = tasks,
            now = now,
            leadMinutes = leadMinutes,
            startingEnabled = startingEnabled,
        )
        startingAlarmScheduler.scheduleAll(plan)
    }
}
