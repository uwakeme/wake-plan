package com.wakeplan.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wakeplan.app.MainActivity
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.TaskCompletion
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 通知内容 — 纯 Kotlin，可单测
 *
 * builder 自己（buildStarting/buildOverdue）需要 Context，不参与单测；单测只覆盖
 * NotificationContent 计算逻辑（title / text 拼接），保证文案符合 PRD 描述
 */
data class NotificationContent(
    val channelId: String,
    val title: String,
    val text: String,
)

object NotificationContentBuilder {

    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun buildStarting(
        completion: TaskCompletion,
        taskTitle: String,
        leadMinutes: Int,
    ): NotificationContent {
        val timeText = "${format(completion.plannedStart)} — ${format(completion.plannedEnd)}"
        val leadText = if (leadMinutes > 0) "${leadMinutes} 分钟后开始" else "即将开始"
        return NotificationContent(
            channelId = Channel.TASK_STARTING.id,
            title = taskTitle,
            text = "$leadText · $timeText",
        )
    }

    fun buildOverdue(
        completion: TaskCompletion,
        taskTitle: String,
        overdueCount: Int,
    ): NotificationContent {
        val title = if (overdueCount == 1) "${taskTitle} 已超时" else "${taskTitle} 仍超时 ($overdueCount/3)"
        val text = "计划结束时间 ${format(completion.plannedEnd)} 已过"
        return NotificationContent(
            channelId = Channel.TASK_OVERDUE.id,
            title = title,
            text = text,
        )
    }

    /** v0.4 实装 task_completed 渠道：打卡完成确认，超时会带上时长 */
    fun buildCompleted(
        completion: TaskCompletion,
        taskTitle: String,
    ): NotificationContent {
        val onTimeText = if (completion.overrunMinutes > 0) "超时 ${completion.overrunMinutes} 分钟" else "按时完成"
        val endTime = completion.actualEnd ?: completion.plannedEnd
        return NotificationContent(
            channelId = Channel.TASK_COMPLETED.id,
            title = "$taskTitle 已完成",
            text = "完成于 ${format(endTime)} · $onTimeText",
        )
    }

    private fun format(dt: LocalDateTime): String = dt.format(TIME_FMT)
}

/**
 * 通知 Builder — Phase 3.1
 *
 * 负责 ContentIntent (deep link → MainActivity) + NotificationCompat.Builder 包成 Notification
 * 内容计算见 [NotificationContentBuilder]（可单测）
 */
object NotificationBuilder {

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_COMPLETION_ID = "extra_completion_id"
    private const val DEEP_LINK_REQUEST_BASE = 1001

    fun buildStarting(
        context: Context,
        completion: TaskCompletion,
        taskTitle: String,
        leadMinutes: Int,
    ): Notification {
        val content = NotificationContentBuilder.buildStarting(completion, taskTitle, leadMinutes)
        return NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(buildContentIntent(context, completion))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun buildOverdue(
        context: Context,
        completion: TaskCompletion,
        taskTitle: String,
        overdueCount: Int,
    ): Notification {
        val content = NotificationContentBuilder.buildOverdue(completion, taskTitle, overdueCount)
        return NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(buildContentIntent(context, completion))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /** v0.4：完成确认通知（低优先级，不响铃不弹头） */
    fun buildCompleted(
        context: Context,
        completion: TaskCompletion,
        taskTitle: String,
    ): Notification {
        val content = NotificationContentBuilder.buildCompleted(completion, taskTitle)
        return NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(buildContentIntent(context, completion))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildContentIntent(context: Context, completion: TaskCompletion): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, completion.taskId)
            putExtra(EXTRA_COMPLETION_ID, completion.id)
        }
        return PendingIntent.getActivity(
            context,
            DEEP_LINK_REQUEST_BASE + completion.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
