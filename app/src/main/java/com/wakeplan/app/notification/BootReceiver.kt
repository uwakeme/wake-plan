package com.wakeplan.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统重启 / 时间变更 / App 更新后重排 starting 闹钟 — v0.4，v0.4.1 补 MY_PACKAGE_REPLACED
 *
 * AlarmManager 挂的闹钟不跨重启（也不跨 App 更新）：v0.3 之前没有 receiver，
 * 重启后当天剩余提醒全部丢失，要打开 App（ensureTodayCompletions）才恢复。
 * 现在收到下列任一广播后静默跑一遍 ScheduleRefresher（补实例 + 重排）。
 *
 * TIME_SET / TIMEZONE_CHANGED 顺手监听：triggerAt 是本地时间换算的绝对毫秒，
 * 时区或系统时间变了要重排才准。MY_PACKAGE_REPLACED：App 自更新后闹钟同样被
 * 系统清掉（v0.4.1 前漏了这个，更新后要等用户打开 App 才恢复）。
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleRefresher: ScheduleRefresher
    @Inject lateinit var clock: java.time.Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduleRefresher.refresh(LocalDate.now(clock))
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
