package com.wakeplan.app.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日回顾调度 — v0.5（PRD US-10 plan_recap 渠道实装）
 *
 * PeriodicWork 24h + initialDelay 对齐到下一个 21:00（PRD 定值）。
 * UPDATE 策略：装过旧 spec 的设备升级时重建。开关关闭时调 [stop]；
 * Worker 自身也读偏好自守（关则空转直接返回），双保险。
 */
@Singleton
class RecapScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun start() {
        val now = LocalDateTime.now()
        val request = PeriodicWorkRequestBuilder<RecapWorker>(
            repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS,
        )
            .setInitialDelay(delayUntilNextRecap(now).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun stop() {
        workManager.cancelUniqueWork(UNIQUE_NAME)
    }

    companion object {
        const val UNIQUE_NAME = "wakeplan_daily_recap"

        /** PRD 定值：每日 21:00 */
        val RECAP_TIME: LocalTime = LocalTime.of(21, 0)

        /**
         * 距下一个回顾时刻的时长：
         * - 今天 21:00 前 → 到今天 21:00
         * - 21:00 及以后（含恰好）→ 到明天 21:00（避免边界重复触发）
         */
        fun delayUntilNextRecap(now: LocalDateTime): Duration {
            val todayRecap = now.toLocalDate().atTime(RECAP_TIME)
            return if (now.isBefore(todayRecap)) {
                Duration.between(now, todayRecap)
            } else {
                Duration.between(now, todayRecap.plusDays(1))
            }
        }
    }
}
