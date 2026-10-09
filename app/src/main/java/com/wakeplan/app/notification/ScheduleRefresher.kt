package com.wakeplan.app.notification

import androidx.room.withTransaction
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.usecase.ScheduleManager
import com.wakeplan.app.domain.usecase.TaskCompletionInstantiator
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 当天调度刷新 — v0.4 从 TodayViewModel.ensureTodayCompletions 抽出，v0.4.1 收敛职责
 *
 * 只做两件事（事务内补实例 + 重排闹钟）：
 * 1. 给当天缺 PENDING 实例的任务补建（insertIfAbsent，不覆盖已有行）
 * 2. 按当前偏好重排 starting 闹钟（scheduleAll 内部先全量取消再挂）
 *
 * 「任务定义变更后的窗口同步」不在这里做（v0.4 的时长漂移同步已移到
 * TaskRepositoryImpl.update）——刷新可能由链式延期落库触发，若每次都把窗口
 * 重置回任务定义，会把持久化的延期结果抹掉。
 *
 * 入口：
 * - TodayViewModel：打开 App / 跨天翻日 / 完成任务后
 * - BootReceiver：系统重启 / 时间或时区变更 / App 更新后
 * - SettingsViewModel：通知偏好（lead / 开关）变更后
 */
@Singleton
class ScheduleRefresher @Inject constructor(
    private val db: WakePlanDatabase,
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskCompletionInstantiator: TaskCompletionInstantiator,
    private val scheduleManager: ScheduleManager,
    private val startingAlarmScheduler: StartingAlarmScheduler,
    private val userPreferences: UserPreferences,
    private val clock: Clock,
) {

    /**
     * 刷新 [date]（一般是今天）的 completion 与 starting 闹钟
     */
    suspend fun refresh(date: LocalDate = LocalDate.now(clock)) {
        // 事务内补实例：BootReceiver 与 TodayViewModel 并发 refresh 时
        // 不会对同一 (task_id, scheduled_date) 双插换 id（换 id 会让已挂闹钟失效）
        db.withTransaction {
            val plans = planRepository.observeAll().first()
            val tasks = taskRepository.observeAll().first()
            val existingTaskIds = taskCompletionRepository.observeByDate(date).first()
                .map { it.taskId }.toSet()

            // 归档计划不铺当天实例、不参与调度（v0.4）
            plans.filter { it.isActive && !it.archived }.forEach { plan ->
                taskCompletionInstantiator(
                    plan = plan,
                    tasks = tasks.filter { it.planId == plan.id },
                    date = date,
                )
                    .filter { it.taskId !in existingTaskIds }
                    .forEach { taskCompletionRepository.insertIfAbsent(it) }
            }
        }

        val todaysCompletions = taskCompletionRepository.observeByDate(date).first()
        val now = LocalDateTime.now(clock)
        val leadMinutes = userPreferences.taskStartingLeadMinutes.first()
        val startingEnabled = userPreferences.taskStartingEnabled.first()
        val plan = scheduleManager(
            completions = todaysCompletions,
            tasks = taskRepository.observeAll().first(),
            now = now,
            leadMinutes = leadMinutes,
            startingEnabled = startingEnabled,
        )
        startingAlarmScheduler.scheduleAll(plan)
    }
}
