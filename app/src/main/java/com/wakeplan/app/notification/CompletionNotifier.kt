package com.wakeplan.app.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.model.TaskCompletion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 任务完成确认通知 — v0.4 实装 task_completed 渠道（PRD US-10 P2）
 *
 * 打卡完成时触发；偏好默认关闭，开关在设置页通知区。
 * 双重门槛：偏好开启 + POST_NOTIFICATIONS 已授权，否则静默跳过。
 */
@Singleton
class CompletionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {

    suspend fun notifyCompleted(completion: TaskCompletion, taskTitle: String) {
        if (!userPreferences.taskCompletedEnabled.first()) return
        if (!NotificationPermissionChecker.hasPostNotificationsPermission(context)) return
        val notification = NotificationBuilder.buildCompleted(
            context = context,
            completion = completion,
            taskTitle = taskTitle,
        )
        NotificationManagerCompat.from(context).notify(notificationIdFor(completion.id), notification)
    }

    companion object {
        /** 与 starting alarm 的 id 段错开，避免互相覆盖 */
        const val ID_BASE = 4000

        fun notificationIdFor(completionId: String): Int = ID_BASE + completionId.hashCode()
    }
}
