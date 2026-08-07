package com.wakeplan.app

import android.app.Application
import com.wakeplan.app.data.db.DatabaseSeeder
import com.wakeplan.app.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Application — @HiltAndroidApp 开启 Hilt 编译期生成
 * 启动时同时初始化 SQLCipher native libs + DatabaseSeeder.seedIfEmpty + NotificationChannels.createAll
 */
@HiltAndroidApp
class App : Application() {

    // Application 级 CoroutineScope，用于启动时的 seed 任务
    private val applicationScope = CoroutineScope(SupervisorJob())

    @Inject lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()
        // SQLCipher 4.6.1 AAR 损坏暂不加载 native lib；v0.1 用 Room 默认 SQLite
        // Seed 3 个内置模板（first install 才插入）
        databaseSeeder.seedIfEmpty(applicationScope)
        // 创建 4 个通知渠道（Android 8+；幂等；用户对 importance 的修改会被系统保留）
        NotificationChannels.createAll(this)
    }
}
