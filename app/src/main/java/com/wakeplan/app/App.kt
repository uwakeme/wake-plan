package com.wakeplan.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.wakeplan.app.data.db.DatabaseSeeder
import com.wakeplan.app.notification.NotificationChannels
import com.wakeplan.app.notification.OverdueWorkScheduler
import com.wakeplan.app.notification.WorkManagerConfigProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Application — @HiltAndroidApp 开启 Hilt 编译期生成
 * 启动时同时初始化：
 * - WorkManager（用 HiltWorkerFactory 注入 @HiltWorker）
 * - DatabaseSeeder.seedIfEmpty（首启 seed 3 个内置模板）
 * - NotificationChannels.createAll（4 个通知渠道）
 * - OverdueWorkScheduler.start（启动 10min 周期检查）
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    // Application 级 CoroutineScope，用于启动时的 seed 任务
    private val applicationScope = CoroutineScope(SupervisorJob())

    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var workManagerConfigProvider: WorkManagerConfigProvider
    @Inject lateinit var overdueWorkScheduler: OverdueWorkScheduler

    override fun onCreate() {
        super.onCreate()
        // 手动初始化 WorkManager（用 HiltWorkerFactory）— Manifest 已禁用自动初始化
        WorkManager.initialize(this, workManagerConfigProvider.createConfiguration())
        // SQLCipher 4.6.1 AAR 损坏暂不加载 native lib；v0.1 用 Room 默认 SQLite
        // Seed 3 个内置模板（first install 才插入）
        databaseSeeder.seedIfEmpty(applicationScope)
        // 创建 4 个通知渠道（Android 8+；幂等；用户对 importance 的修改会被系统保留）
        NotificationChannels.createAll(this)
        // 启动 10min 周期 overdue 检查（KEEP 策略：已有同名 work 就不重建）
        overdueWorkScheduler.start()
    }

    // WorkManager Configuration.Provider — WorkManager.getInstance(context) 时如果未初始化会调这个
    override val workManagerConfiguration: Configuration
        get() = workManagerConfigProvider.createConfiguration()
}
