package com.wakeplan.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.wakeplan.app.data.db.DatabaseSeeder
import com.wakeplan.app.data.security.DatabaseKeyStore
import com.wakeplan.app.notification.NotificationChannels
import com.wakeplan.app.notification.OverdueWorkScheduler
import com.wakeplan.app.notification.WorkManagerConfigProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Application — @HiltAndroidApp 开启 Hilt 编译期生成
 * 启动时同时初始化：
 * - WorkManager（用 HiltWorkerFactory 注入 @HiltWorker）
 * - DatabaseKeyStore bootstrap：Keystore master key 生成 + 第一次 wrap 32 字节随机 passphrase
 *   → 同步跑（< 100ms）；后续 DatabaseModule.provideDatabase 拿 cached
 * - DatabaseSeeder.seedIfEmpty（首启 seed 3 个内置模板）
 * - NotificationChannels.createAll（4 个通知渠道）
 * - OverdueWorkScheduler.start（启动 10min 周期检查）
 *
 * Phase 5.8 SQLCipher：
 * - System.loadLibrary("sqlcipher") 必须在任何 SupportOpenHelperFactory 代码路径前跑
 *   （Room.databaseBuilder → openHelperFactory 内部会访问 native）
 * - Keystore AES-GCM 操作在 IO 线程，< 50ms；runBlocking 阻塞主线程短暂可接受
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    // Application 级 CoroutineScope，用于启动时的 seed 任务
    private val applicationScope = CoroutineScope(SupervisorJob())

    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var workManagerConfigProvider: WorkManagerConfigProvider
    @Inject lateinit var overdueWorkScheduler: OverdueWorkScheduler
    @Inject lateinit var databaseKeyStore: DatabaseKeyStore

    override fun onCreate() {
        super.onCreate()
        // 1. SQLCipher native lib（必须在 DatabaseModule 解析前；v0.1 注释掉的 4.5.x 系列不兼容 Android 15+）
        System.loadLibrary("sqlcipher")
        // 2. 手动初始化 WorkManager（用 HiltWorkerFactory）— Manifest 已禁用自动初始化
        WorkManager.initialize(this, workManagerConfigProvider.createConfiguration())
        // 3. 同步 bootstrap DB passphrase（Keystore AES-GCM < 50ms；之后 DatabaseModule 走 cached）
        runBlocking(Dispatchers.IO) {
            databaseKeyStore.getOrCreatePassphrase()
        }
        // 4. Seed 3 个内置模板（first install 才插入）
        databaseSeeder.seedIfEmpty(applicationScope)
        // 5. 创建 4 个通知渠道（Android 8+；幂等；用户对 importance 的修改会被系统保留）
        NotificationChannels.createAll(this)
        // 6. 启动 10min 周期 overdue 检查（KEEP 策略：已有同名 work 就不重建）
        overdueWorkScheduler.start()
    }

    // WorkManager Configuration.Provider — WorkManager.getInstance(context) 时如果未初始化会调这个
    override val workManagerConfiguration: Configuration
        get() = workManagerConfigProvider.createConfiguration()
}
