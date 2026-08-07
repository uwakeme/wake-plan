package com.wakeplan.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application — @HiltAndroidApp 开启 Hilt 编译期生成
 * 启动时同时初始化 SQLCipher native libs（必须在任何 DAO 调用之前）
 */
@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // SQLCipher native libs 加载（Phase 0 用占位密钥时也必须先 loadLibrary）
        net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
    }
}
