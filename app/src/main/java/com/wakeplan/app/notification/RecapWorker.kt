package com.wakeplan.app.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.usecase.StatsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 每日回顾 Worker — v0.5（PRD US-10：每日 21:00 当日完成率小结）
 *
 * 分子 = 当日 COMPLETED 实例数；分母沿用 v0.4.1 的任务定义推导口径
 * （StatsUseCase.dayBucket），没打开 App 的日子也不会把完成率稀释成 0%。
 * 当天没有任何应生效任务时静默跳过（没有可回顾的内容）。
 * 点击通知 → Today tab。
 */
@HiltWorker
class RecapWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val completionRepository: TaskCompletionRepository,
    private val statsUseCase: StatsUseCase,
    private val preferences: UserPreferences,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!preferences.planRecapEnabled.first()) return Result.success()

        val today = LocalDate.now(clock)
        val completions = completionRepository
            .observeByDateRangeInclusive(today.minusDays(1), today).first()
            .filter { it.scheduledDate == today }
        val plans = planRepository.observeAll().first()
        val tasks = taskRepository.observeAll().first()
        val bucket = statsUseCase.dayBucket(completions, plans, tasks, today)

        // 没安排任何任务的日子不推送（避免空洞通知打扰）
        if (bucket.expectedCount <= 0) return Result.success()

        // Android 13+ 未授权 POST_NOTIFICATIONS 时静默跳过（lint MissingPermission）
        if (!NotificationPermissionChecker.hasPostNotificationsPermission(applicationContext)) {
            return Result.success()
        }

        val notification = NotificationBuilder.buildRecap(
            context = applicationContext,
            completedCount = bucket.completedCount,
            expectedCount = bucket.expectedCount,
        )
        val notifier = NotificationManagerCompat.from(applicationContext)
        try {
            notifier.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 授权在两次调用间被收回，跳过
        }
        return Result.success()
    }

    companion object {
        /** 固定 id：每天覆盖前一天的通知，不堆积 */
        const val NOTIFICATION_ID = 5100
    }
}
