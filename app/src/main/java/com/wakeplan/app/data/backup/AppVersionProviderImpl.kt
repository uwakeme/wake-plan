package com.wakeplan.app.data.backup

import com.wakeplan.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 版本信息提供实现 — Phase 4.1
 *
 * 走 BuildConfig 而非 PackageManager：BuildConfig 是编译期常量，无反射/IO
 * 跟 AGP 8.x 的 buildFeatures.buildConfig = true 配套（前面已开）
 */
@Singleton
class AppVersionProviderImpl @Inject constructor() : AppVersionProvider {
    override val versionName: String = BuildConfig.VERSION_NAME
    override val versionCode: Int = BuildConfig.VERSION_CODE
}
