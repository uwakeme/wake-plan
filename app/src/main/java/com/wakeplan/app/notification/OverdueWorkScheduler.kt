package com.wakeplan.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Overdue WorkManager 调度 — Phase 3.2
 *
 * 每 15 分钟跑一次 OverdueCheckWorker——WorkManager 周期任务 15 分钟是硬下限，
 * v0.4.1 前声明 10 分钟实际也 ≥15 分钟且 lint 报警；提醒节奏语义（10 分钟一档、
 * 最多 3 次）由 OverdueCheckWorker 按超时时长推导，与检查周期解耦。
 *
 * UPDATE 策略：老版本装过 10 分钟 spec 的设备升级时用新周期重建
 * （KEEP 会永远保留旧的 10 分钟 spec）。
 *
 * WorkManager 初始化在 App.onCreate 里用 HiltWorkerFactory
 */
@Singleton
class OverdueWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun start() {
        val request = PeriodicWorkRequestBuilder<OverdueCheckWorker>(
            repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
        ).build()
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
        const val UNIQUE_NAME = "wakeplan_overdue_check"
    }
}

/**
 * WorkManager Configuration Provider — Hilt 注入
 *
 * 让 HiltWorkerFactory 替换默认 WorkerFactory，让 @HiltWorker 自动注入依赖
 * App.onCreate 里调 WorkManager.initialize(this, workManagerConfiguration)
 */
@Singleton
class WorkManagerConfigProvider @Inject constructor(
    private val hiltWorkerFactory: HiltWorkerFactory,
) {
    fun createConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()
}
