package com.wakeplan.app.notification

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * 通知权限检查 — Phase 3.1
 *
 * 三类权限：
 * 1. POST_NOTIFICATIONS（Android 13+ TIRAMISU）：运行时权限，用户拒绝后只能去系统设置打开
 * 2. SCHEDULE_EXACT_ALARM（Android 12+ S）：精确闹钟，install-time；Android 13+ 用户可在设置里关
 * 3. USE_EXACT_ALARM（Android 13+ TIRAMISU）：精确闹钟，install-time + 不可关闭（OEM 限定）
 *
 * v0.1 实装 starting（AlarmManager.setExactAndAllowWhileIdle）依赖 #2 或 #3
 */
object NotificationPermissionChecker {

    /**
     * POST_NOTIFICATIONS 权限（Android 13+ 必需；< 13 默认允许）
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 能否调度精确闹钟
     * - Android 12 (S/API 31)+：需要 SCHEDULE_EXACT_ALARM，且用户没在系统设置里关掉
     * - Android 13+：可声明 USE_EXACT_ALARM 作为不可关闭的精确闹钟（仅限 timer/alarm 类应用）
     * - < 12：默认允许
     *
     * v0.1 用 SCHEDULE_EXACT_ALMANAGER.canScheduleExactAlarms() 主动检查
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService<AlarmManager>() ?: return false
        return manager.canScheduleExactAlarms()
    }
}
