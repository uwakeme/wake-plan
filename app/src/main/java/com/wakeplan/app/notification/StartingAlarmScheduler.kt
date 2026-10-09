package com.wakeplan.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.wakeplan.app.domain.usecase.SchedulePlan
import com.wakeplan.app.domain.usecase.StartingTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务 starting alarm 调度 — Phase 3.2
 *
 * 调 AlarmManager.setExactAndAllowWhileIdle（精确闹钟，绕过 Doze）
 * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限，调用方应先用 [NotificationPermissionChecker.canScheduleExactAlarms] 检查
 *
 * trigger 走 Intent action ACTION_TASK_STARTING（StartingAlarmReceiver 接收）
 */
@Singleton
class StartingAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager? = context.getSystemService()

    /** 本进程内通过 scheduleAll 挂过的 requestCode 集合——cancelAll 用它精确反注册 */
    private val scheduledRequestCodes: MutableSet<Int> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    /**
     * 调度所有 starting trigger
     * 先全量取消本进程挂过的闹钟再挂新的——偏好关闭 / 计划变化后旧闹钟不会残留
     */
    fun scheduleAll(plan: SchedulePlan) {
        val mgr = alarmManager ?: return
        cancelAllStartingAlarms()
        plan.startingTriggers.forEach { trigger ->
            val pi = buildPendingIntent(trigger)
            val triggerAtMillis = trigger.triggerAt
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (mgr.canScheduleExactAlarms()) {
                        mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    } else {
                        // 用户没授权精确闹钟，降级到 inexact（保底）
                        mgr.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    }
                } else {
                    mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            } catch (e: SecurityException) {
                // Android 12+ 偶尔 throw（用户授权 race condition）— 降级
                mgr.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            scheduledRequestCodes.add(requestCodeFor(trigger.completionId))
        }
    }

    /** 取消某个 completion 的 starting alarm（用户点了"开始" / "跳过" / 任务已 COMPLETED） */
    fun cancelStartingAlarm(completionId: String) {
        cancelPendingIntent(requestCodeFor(completionId))
        scheduledRequestCodes.remove(requestCodeFor(completionId))
    }

    /**
     * 全量取消（重新 schedule / 用户关闭 starting 偏好时调用）。
     * v0.4.1 实装：之前是空实现，关闭提醒开关后已挂闹钟照响。
     * 进程重启后本集合为空，残留闹钟由 Receiver 的 PENDING 状态守卫静默吞掉。
     */
    fun cancelAllStartingAlarms() {
        scheduledRequestCodes.forEach { cancelPendingIntent(it) }
        scheduledRequestCodes.clear()
    }

    /** 按 requestCode 取消闹钟——extras 不参与 Intent 匹配，action+component 相同即可 */
    private fun cancelPendingIntent(requestCode: Int) {
        val mgr = alarmManager ?: return
        val intent = Intent(context, StartingAlarmReceiver::class.java).apply {
            action = ACTION_TASK_STARTING
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) mgr.cancel(pi)
    }

    private fun buildPendingIntent(trigger: StartingTrigger): PendingIntent {
        val intent = Intent(context, StartingAlarmReceiver::class.java).apply {
            action = ACTION_TASK_STARTING
            putExtra(EXTRA_COMPLETION_ID, trigger.completionId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(trigger.completionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_TASK_STARTING = "com.wakeplan.app.ACTION_TASK_STARTING"
        const val EXTRA_COMPLETION_ID = "extra_completion_id"
        private const val REQUEST_CODE_BASE = 2000

        fun requestCodeFor(completionId: String): Int =
            REQUEST_CODE_BASE + completionId.hashCode()
    }
}
