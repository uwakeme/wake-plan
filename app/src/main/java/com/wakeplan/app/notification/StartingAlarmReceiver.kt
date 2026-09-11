package com.wakeplan.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务 starting 闹钟接收器 — Phase 3.2
 *
 * AlarmManager.setExactAndAllowWhileIdle 触发 → 构造 starting 通知 → notify
 * 用户点击通知 → MainActivity (deep link) → TodayScreen 定位任务（Phase 3.3 接住）
 *
 * @AndroidEntryPoint 注入 Hilt 仓库
 */
@AndroidEntryPoint
class StartingAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var completionRepository: TaskCompletionRepository
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var preferences: com.wakeplan.app.data.prefs.UserPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StartingAlarmScheduler.ACTION_TASK_STARTING) return
        val completionId = intent.getStringExtra(StartingAlarmScheduler.EXTRA_COMPLETION_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val completion = completionRepository.getById(completionId) ?: return@launch
                if (completion.status != com.wakeplan.app.domain.model.TaskStatus.PENDING) return@launch
                // Android 13+ 未授权时静默跳过（lint MissingPermission）
                if (!NotificationPermissionChecker.hasPostNotificationsPermission(context)) return@launch
                val task = taskRepository.getById(completion.taskId) ?: return@launch
                val leadMinutes = preferences.taskStartingLeadMinutes.first()
                val notification = NotificationBuilder.buildStarting(
                    context = context,
                    completion = completion,
                    taskTitle = task.title,
                    leadMinutes = leadMinutes,
                )
                NotificationManagerCompat.from(context).let { notifier ->
                    try {
                        notifier.notify(notificationIdFor(completionId), notification)
                    } catch (_: SecurityException) {
                        // 通知权限被收回，静默跳过
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun notificationIdFor(completionId: String): Int =
            StartingAlarmScheduler.requestCodeFor(completionId) + 1
    }
}
