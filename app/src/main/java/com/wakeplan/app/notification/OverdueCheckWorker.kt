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
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Overdue 周期检查 Worker — Phase 3.2
 *
 * 每 10 分钟被 WorkManager PeriodicWorkRequest 触发一次
 * 查所有 active plan 当日所有 PENDING/IN_PROGRESS 且 plannedEnd < now 的 completion
 * 对每个推送 overdue 通知（v0.1 不维护 count 次数；v0.2 引入 count 字段）
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val overdueEnabled = preferences.taskOverdueEnabled.first()
        if (!overdueEnabled) return Result.success()

        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val completions = completionRepository.observeByDate(today).first()
        val overdue = completions.filter {
            (it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS)
                && it.plannedEnd.isBefore(now)
        }
        if (overdue.isEmpty()) return Result.success()
        // Android 13+ 未授权 POST_NOTIFICATIONS 时静默跳过（lint MissingPermission）
        if (!NotificationPermissionChecker.hasPostNotificationsPermission(applicationContext)) {
            return Result.success()
        }

        val notifier = NotificationManagerCompat.from(applicationContext)
        overdue.forEach { c ->
            val task = taskRepository.getById(c.taskId) ?: return@forEach
            val notification = NotificationBuilder.buildOverdue(
                context = applicationContext,
                completion = c,
                taskTitle = task.title,
                overdueCount = 1,  // v0.1 简化：永远是 1
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
    }
}
