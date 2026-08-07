package com.wakeplan.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * 通知渠道 — Phase 3.1
 *
 * PRD US-10 / AC-10.1：4 个 Channel
 * - `task_starting` (P0, v0.1 启用)：任务即将开始（默认 5 分钟前，可配置 0/5/10/15）
 * - `task_overdue` (P0, v0.1 启用)：任务超时未完成（每 10 分钟一次，最多 3 次）
 * - `task_completed` (P2, v0.1 占位)：任务完成确认（默认关闭，v0.2 启用）
 * - `plan_recap` (P2, v0.1 占位)：每日 21:00 当日完成率小结（默认关闭，v0.2 启用）
 *
 * Channel 在 App.onCreate 一次性创建；之后 importance 修改无效（系统保留用户修改）
 */
enum class Channel(
    val id: String,
    val displayName: String,
    val description: String,
    val importance: Int,
) {
    TASK_STARTING(
        id = "task_starting",
        displayName = "任务开始提醒",
        description = "任务即将开始前 X 分钟提醒（默认 5 分钟）",
        importance = NotificationManager.IMPORTANCE_HIGH,
    ),
    TASK_OVERDUE(
        id = "task_overdue",
        displayName = "任务超时",
        description = "任务超时未完成时循环提醒（每 10 分钟，最多 3 次）",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    ),
    TASK_COMPLETED(
        id = "task_completed",
        displayName = "任务完成确认",
        description = "任务完成时推送确认通知（v0.1 关闭，v0.2 启用）",
        importance = NotificationManager.IMPORTANCE_LOW,
    ),
    PLAN_RECAP(
        id = "plan_recap",
        displayName = "每日计划回顾",
        description = "每日 21:00 推送当日完成率小结（v0.1 关闭，v0.2 启用）",
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
            val channel = NotificationChannel(ch.id, ch.displayName, ch.importance).apply {
                description = ch.description
            }
            manager.createNotificationChannel(channel)
        }
    }
}
