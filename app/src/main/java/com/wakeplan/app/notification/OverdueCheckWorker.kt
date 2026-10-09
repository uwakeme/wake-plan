package com.wakeplan.app.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Overdue 周期检查 Worker — Phase 3.2，v0.4.1 修正两处规格偏差
 *
 * - 次数上限（PRD US-10：最多 3 次）：按"已超时分钟数"推导第几次提醒
 *   （每 10 分钟一档），超过 30 分钟不再打扰——无需落库计数，天然封顶
 * - 跨午夜：查 [昨天, 今天] 两天的 completion，昨天 23:00–今天 00:30 的尾巴
 *   拖到今天凌晨未完成也会报超时（v0.4.1 前只看当天，全部漏报）
 *
 * @HiltWorker 自动注入 Repository / UserPreferences（hilt-work runtime + compiler 已就绪）
 */
@HiltWorker
class OverdueCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val completionRepository: TaskCompletionRepository,
    private val taskRepository: TaskRepository,
    private val preferences: UserPreferences,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val overdueEnabled = preferences.taskOverdueEnabled.first()
        if (!overdueEnabled) return Result.success()

        val now = LocalDateTime.now(clock)
        val today = LocalDate.now(clock)
        val todayStart = today.atStartOfDay()

        // 含昨天的跨午夜尾巴：plannedEnd 落在今天 00:00 之后才算"拖到今天"
        val completions = completionRepository
            .observeByDateRangeInclusive(today.minusDays(1), today).first()
        val overdue = completions.filter {
            (it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS) &&
                it.plannedEnd.isAfter(todayStart) &&
                it.plannedEnd.isBefore(now)
        }
        if (overdue.isEmpty()) return Result.success()
        // Android 13+ 未授权 POST_NOTIFICATIONS 时静默跳过（lint MissingPermission）
        if (!NotificationPermissionChecker.hasPostNotificationsPermission(applicationContext)) {
            return Result.success()
        }

        val notifier = NotificationManagerCompat.from(applicationContext)
        overdue.forEach { c ->
            val overdueCount = overdueCountFor(c.plannedEnd, now)
            if (overdueCount > MAX_OVERDUE_NOTIFICATIONS) return@forEach  // 已提醒满 3 次
            val task = taskRepository.getById(c.taskId) ?: return@forEach
            val notification = NotificationBuilder.buildOverdue(
                context = applicationContext,
                completion = c,
                taskTitle = task.title,
                overdueCount = overdueCount,
            )
            try {
                notifier.notify(notificationIdFor(c.id), notification)
            } catch (_: SecurityException) {
                // 通知权限被收回，跳过本轮
            }
        }
        return Result.success()
    }

    companion object {
        fun notificationIdFor(completionId: String): Int =
            StartingAlarmScheduler.requestCodeFor(completionId) + 2

        /** PRD US-10：每 10 分钟一档、最多 3 次 */
        const val OVERDUE_INTERVAL_MINUTES = 10L
        const val MAX_OVERDUE_NOTIFICATIONS = 3

        /** 已超时多久 → 第几次提醒（1..N）；超 30 分钟后返回 >3，调用方据此静默 */
        fun overdueCountFor(plannedEnd: LocalDateTime, now: LocalDateTime): Int {
            val minutesOverdue = Duration.between(plannedEnd, now).toMinutes()
            return (minutesOverdue / OVERDUE_INTERVAL_MINUTES).toInt() + 1
        }
    }
}
