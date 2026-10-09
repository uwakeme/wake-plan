package com.wakeplan.app.focus

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wakeplan.app.MainActivity
import com.wakeplan.app.R
import com.wakeplan.app.notification.Channel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 专注计时前台 Service — v0.6
 *
 * 只做两件事：保持进程存活 + 把 [FocusTimerController.state] 渲染成常驻倒计时通知。
 * 状态与计时的真源在 controller（单例），Service 随场次起停。
 *
 * 通知动作（纯按钮）：
 * - [ACTION_COMPLETE]：完成打卡（专注阶段）→ controller.completeTask()
 * - [ACTION_NEXT]：进入下一阶段 → controller.advance() / skipPhase()
 * - [ACTION_ABANDON]：放弃整场 → controller.abandon()
 *
 * FGS 类型 specialUse（API 34+ 必须声明类型；计时器类应用的标准选择，
 * manifest 带 PROPERTY_SPECIAL_USE_FGS_SUBTYPE 声明）。
 */
@AndroidEntryPoint
class FocusTimerService : Service() {

    @Inject lateinit var controller: FocusTimerController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COMPLETE -> controller.completeTask()
            ACTION_NEXT -> {
                val s = controller.state.value
                if (s != null) {
                    if (s.running) controller.skipPhase() else controller.advance()
                }
            }
            ACTION_ABANDON -> controller.abandon()
            else -> {
                // ACTION_START / 重启：先立 foreground 再订阅状态
                startAsForeground(buildNotification(controller.state.value))
                if (observeJob == null) {
                    observeJob = scope.launch {
                        controller.state.collect { state ->
                            if (state == null) {
                                stopSelf()
                            } else {
                                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                                nm.notify(NOTIFICATION_ID, buildNotification(state))
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: com.wakeplan.app.focus.FocusState?): Notification {
        val ctx = this
        if (state == null) {
            return NotificationCompat.Builder(ctx, Channel.FOCUS_TIMER.id)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(ctx.getString(R.string.focus_notif_title))
                .build()
        }
        val phaseLabel = phaseLabel(ctx, state.phase)
        val remaining = com.wakeplan.app.notification.NotificationBuilder.formatRemaining(
            java.time.Duration.between(java.time.Instant.now(), state.phaseEndsAt),
        )
        val builder = NotificationCompat.Builder(ctx, Channel.FOCUS_TIMER.id)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(state.taskTitle)
            .setContentText(
                if (state.running) {
                    ctx.getString(R.string.focus_notif_running, phaseLabel, remaining)
                } else {
                    ctx.getString(R.string.focus_notif_waiting, phaseLabel)
                },
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(ctx, state.completionId))

        if (state.isFocusPhase) {
            builder.addAction(
                0,
                ctx.getString(R.string.focus_action_complete),
                actionPendingIntent(ctx, ACTION_COMPLETE),
            )
        }
        builder.addAction(
            0,
            if (state.running) ctx.getString(R.string.focus_action_skip)
            else ctx.getString(R.string.focus_action_next),
            actionPendingIntent(ctx, ACTION_NEXT),
        )
        builder.addAction(
            0,
            ctx.getString(R.string.focus_action_abandon),
            actionPendingIntent(ctx, ACTION_ABANDON),
        )
        return builder.build()
    }

    private fun phaseLabel(ctx: Context, phase: FocusPhase): String = when (phase) {
        FocusPhase.FOCUS -> ctx.getString(R.string.focus_phase_focus)
        FocusPhase.SHORT_BREAK -> ctx.getString(R.string.focus_phase_short_break)
        FocusPhase.LONG_BREAK -> ctx.getString(R.string.focus_phase_long_break)
    }

    private fun contentIntent(ctx: Context, completionId: String): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FOCUS_COMPLETION_ID, completionId)
        }
        return PendingIntent.getActivity(
            ctx,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionPendingIntent(ctx: Context, action: String): PendingIntent {
        val intent = Intent(ctx, FocusTimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            ctx,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_START = "com.wakeplan.app.focus.ACTION_START"
        const val ACTION_COMPLETE = "com.wakeplan.app.focus.ACTION_COMPLETE"
        const val ACTION_NEXT = "com.wakeplan.app.focus.ACTION_NEXT"
        const val ACTION_ABANDON = "com.wakeplan.app.focus.ACTION_ABANDON"

        /** 通知点击跳专注页的 extra 名（MainActivity 接） */
        const val EXTRA_FOCUS_COMPLETION_ID = "extra_focus_completion_id"

        const val NOTIFICATION_ID = 5200
        private const val CONTENT_REQUEST_CODE = 1201
    }
}
