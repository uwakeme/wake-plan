package com.wakeplan.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.wakeplan.app.R

/**
 * 通知渠道 — Phase 3.1，v0.4.1 名称/描述走 strings（跟随 App 语言）
 *
 * PRD US-10 / AC-10.1：4 个 Channel + v0.6 专注计时
 * - `task_starting` (P0)：任务即将开始（默认 5 分钟前，可配置 0/5/10/15）
 * - `task_overdue` (P0)：任务超时未完成（WorkManager 周期检查，最多提醒 3 次）
 * - `task_completed` (P2)：任务完成确认（默认关闭，v0.4 实装）
 * - `plan_recap` (P2)：每日回顾（v0.5 实装）
 * - `focus_timer` (v0.6)：专注计时前台常驻通知（静默，只倒计时不响铃）
 *
 * Channel 在 App.onCreate 一次性创建；之后 importance 修改无效（系统保留用户修改）
 */
enum class Channel(
    val id: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val importance: Int,
) {
    TASK_STARTING(
        id = "task_starting",
        nameRes = R.string.channel_starting_name,
        descriptionRes = R.string.channel_starting_desc,
        importance = NotificationManager.IMPORTANCE_HIGH,
    ),
    TASK_OVERDUE(
        id = "task_overdue",
        nameRes = R.string.channel_overdue_name,
        descriptionRes = R.string.channel_overdue_desc,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    ),
    TASK_COMPLETED(
        id = "task_completed",
        nameRes = R.string.channel_completed_name,
        descriptionRes = R.string.channel_completed_desc,
        importance = NotificationManager.IMPORTANCE_LOW,
    ),
    PLAN_RECAP(
        id = "plan_recap",
        nameRes = R.string.channel_recap_name,
        descriptionRes = R.string.channel_recap_desc,
        importance = NotificationManager.IMPORTANCE_LOW,
    ),
    FOCUS_TIMER(
        id = "focus_timer",
        nameRes = R.string.channel_focus_name,
        descriptionRes = R.string.channel_focus_desc,
        importance = NotificationManager.IMPORTANCE_LOW,
    ),
}

object NotificationChannels {

    /**
     * App.onCreate 一次性创建所有 channel
     * O(8) 创建，幂等 — 重复调用同名 channel 不会覆盖用户对 importance 的修改
     */
    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        Channel.values().forEach { ch ->
            val channel = NotificationChannel(
                ch.id,
                context.getString(ch.nameRes),
                ch.importance,
            ).apply {
                description = context.getString(ch.descriptionRes)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
