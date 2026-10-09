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
 * NotificationContent 计算逻辑（title / text 拼接），保证文案符合 PRD 描述。
 *
 * v0.4.1 文案模板从 strings 资源注入（[NotificationTexts.from]），跟随 App 语言；
 * 模板用 %1$s/%1$d 位置参数，跟 values/values-en 的 strings.xml 一一对应。
 */
data class NotificationContent(
    val channelId: String,
    val title: String,
    val text: String,
)

/** 通知文案模板 — 由持有 Context 的调用方构造后传入纯函数层 */
data class NotificationTexts(
    val startingLead: String,      // "%1$d 分钟后开始"
    val startingImminent: String,  // "即将开始"
    val overdueFirst: String,      // "%1$s 已超时"
    val overdueRepeat: String,     // "%1$s 仍超时 (%2$d/3)"
    val overdueBody: String,       // "计划结束时间 %1$s 已过"
    val completedTitle: String,    // "%1$s 已完成"
    val completedOnTime: String,   // "完成于 %1$s · 按时完成"
    val completedOverrun: String,  // "完成于 %1$s · 超时 %2$d 分钟"
    val recapTitle: String,        // "今日回顾"
    val recapBody: String,         // "完成 %1$d/%2$d（%3$d%%）"
) {
    companion object {
        fun from(context: Context) = NotificationTexts(
            startingLead = context.getString(R.string.notif_starting_lead),
            startingImminent = context.getString(R.string.notif_starting_imminent),
            overdueFirst = context.getString(R.string.notif_overdue_first),
            overdueRepeat = context.getString(R.string.notif_overdue_repeat),
            overdueBody = context.getString(R.string.notif_overdue_body),
            completedTitle = context.getString(R.string.notif_completed_title),
            completedOnTime = context.getString(R.string.notif_completed_on_time),
            completedOverrun = context.getString(R.string.notif_completed_overrun),
            recapTitle = context.getString(R.string.notif_recap_title),
            recapBody = context.getString(R.string.notif_recap_body),
        )
    }
}

object NotificationContentBuilder {

    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun buildStarting(
        completion: TaskCompletion,
        taskTitle: String,
        leadMinutes: Int,
        texts: NotificationTexts,
    ): NotificationContent {
        val timeText = "${format(completion.plannedStart)} — ${format(completion.plannedEnd)}"
        val leadText = if (leadMinutes > 0) {
            String.format(texts.startingLead, leadMinutes)
        } else {
            texts.startingImminent
        }
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
        texts: NotificationTexts,
    ): NotificationContent {
        val title = if (overdueCount <= 1) {
            String.format(texts.overdueFirst, taskTitle)
        } else {
            String.format(texts.overdueRepeat, taskTitle, overdueCount)
        }
        return NotificationContent(
            channelId = Channel.TASK_OVERDUE.id,
            title = title,
            text = String.format(texts.overdueBody, format(completion.plannedEnd)),
        )
    }

    /** v0.4 实装 task_completed 渠道：打卡完成确认，超时会带上时长 */
    fun buildCompleted(
        completion: TaskCompletion,
        taskTitle: String,
        texts: NotificationTexts,
    ): NotificationContent {
        val endTime = completion.actualEnd ?: completion.plannedEnd
        val body = if (completion.overrunMinutes > 0) {
            String.format(texts.completedOverrun, format(endTime), completion.overrunMinutes)
        } else {
            String.format(texts.completedOnTime, format(endTime))
        }
        return NotificationContent(
            channelId = Channel.TASK_COMPLETED.id,
            title = String.format(texts.completedTitle, taskTitle),
            text = body,
        )
    }

    /** v0.5：每日回顾——当日完成率小结（expected 为按任务定义推导的分母） */
    fun buildRecap(
        completedCount: Int,
        expectedCount: Int,
        texts: NotificationTexts,
    ): NotificationContent {
        val pct = if (expectedCount <= 0) 0 else (completedCount * 100) / expectedCount
        return NotificationContent(
            channelId = Channel.PLAN_RECAP.id,
            title = texts.recapTitle,
            text = String.format(texts.recapBody, completedCount, expectedCount, pct),
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
    private const val RECAP_NAV_REQUEST_CODE = 1101

    fun buildStarting(
        context: Context,
        completion: TaskCompletion,
        taskTitle: String,
        leadMinutes: Int,
    ): Notification {
        val content = NotificationContentBuilder.buildStarting(
            completion, taskTitle, leadMinutes, NotificationTexts.from(context),
        )
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
        val content = NotificationContentBuilder.buildOverdue(
            completion, taskTitle, overdueCount, NotificationTexts.from(context),
        )
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
        val content = NotificationContentBuilder.buildCompleted(
            completion, taskTitle, NotificationTexts.from(context),
        )
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

    /** v0.4.1：设置页"测试通知"——验证渠道/权限/提醒链路是否活着 */
    fun buildTest(context: Context): Notification {
        val content = NotificationContent(
            channelId = Channel.TASK_STARTING.id,
            title = context.getString(R.string.notif_test_title),
            text = context.getString(R.string.notif_test_text),
        )
        return NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    /** v0.5：每日回顾（低优先级），点击进 Today */
    fun buildRecap(context: Context, completedCount: Int, expectedCount: Int): Notification {
        val content = NotificationContentBuilder.buildRecap(
            completedCount, expectedCount, NotificationTexts.from(context),
        )
        return NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(buildTodayIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 回顾通知没有具体 task 可定位，只跳 Today tab（复用 widget 的导航 extra） */
    private fun buildTodayIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.wakeplan.app.widget.TodayWidgetProvider.EXTRA_NAV_TO_TODAY, true)
        }
        return PendingIntent.getActivity(
            context,
            RECAP_NAV_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
