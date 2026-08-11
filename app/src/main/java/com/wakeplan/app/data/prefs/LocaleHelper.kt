package com.wakeplan.app.data.prefs

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 语言切换管理 — v0.3 P1 US
 *
 * 三种状态：
 * - [LOCALE_SYSTEM] = 跟随系统（默认）
 * - [LOCALE_ZH] = 简体中文
 * - [LOCALE_EN] = English
 *
 * 持久化：SharedPreferences（独立文件，不跟 UserPreferences 混合）
 * 应用：通过 [AppCompatDelegate.setApplicationLocales] — 跨 API 26-35 自动 fallback：
 * - API 33+ 走 system LocaleManager
 * - API < 33 走 appcompat 自己的实现
 *
 * 注意：AppCompatDelegate 的 applicationLocales 本身已经持久化（API 33+ 跟系统，< 33 走 appcompat）
 * 但我们用自己 SharedPreferences 存"tag 字符串"是为了：
 * 1. UI 上能直接展示当前选择（不解析 LocaleListCompat）
 * 2. 用户切到"跟随系统"时能正确调 empty locale list
 */
@Singleton
class LocaleHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 当前用户选择的语言 tag；null = 跟随系统 */
    val currentTag: String? get() = prefs.getString(KEY_TAG, null)

    /** 当前选择（解析为 [LocaleChoice]） */
    val current: LocaleChoice get() = LocaleChoice.fromTag(currentTag)

    /**
     * 应用用户选择 — 写 prefs + 调 AppCompatDelegate
     * 调用后通常要 Activity.recreate() 触发重新加载资源
     */
    fun apply(choice: LocaleChoice) {
        prefs.edit { putString(KEY_TAG, choice.tag) }
        // 跨 API 兼容：API 33+ 直接走 system LocaleManager（最稳，system 持久化 + 通知 system locale picker）
        // < API 33 走 AppCompat 1.7.0 包装
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val localeList = when (choice) {
                LocaleChoice.SYSTEM -> LocaleList.getEmptyLocaleList()
                LocaleChoice.ZH -> LocaleList(Locale("zh", "CN"))
                LocaleChoice.EN -> LocaleList(Locale.ENGLISH)
            }
            localeManager.applicationLocales = localeList
            android.util.Log.i(
                "LocaleHelper",
                "API33+ LocaleManager.applicationLocales=$localeList",
            )
        } else {
            AppCompatDelegate.setApplicationLocales(choice.toLocaleList())
            android.util.Log.i(
                "LocaleHelper",
                "AppCompat.setApplicationLocales=${choice.toLocaleList()}",
            )
        }
    }

    /**
     * App 启动时调一次：从 prefs 恢复上次选择（如果用户切过）
     * 已经在 Android 13+ 系统里切过的话，AppCompatDelegate.appLocales 已经自动恢复；
     * 但自己 SharedPreferences 也要同步一次以防 tag 漂移
     */
    fun restoreOnStartup() {
        val stored = current
        // AppCompatDelegate.appLocales 已经是 system/appcompat 持久化后的值
        // 我们把系统感知到的值同步进自己的 prefs（如果是 empty 表示跟随系统）
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val effectiveTag = if (appLocales.isEmpty) {
            null
        } else {
            // 取第一个 locale tag
            appLocales[0]?.toLanguageTag()
        }
        if (effectiveTag != currentTag) {
            prefs.edit { putString(KEY_TAG, effectiveTag) }
        }
    }

    enum class LocaleChoice(val tag: String?, val displayKey: String) {
        SYSTEM(null, "system"),
        ZH("zh-CN", "zh"),
        EN("en", "en"),
        ;

        fun toLocaleList(): LocaleListCompat = when (this) {
            SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            ZH -> LocaleListCompat.create(Locale("zh", "CN"))
            EN -> LocaleListCompat.create(Locale.ENGLISH)
        }

        companion object {
            fun fromTag(tag: String?): LocaleChoice = when (tag) {
                "zh-CN" -> ZH
                "en" -> EN
                else -> SYSTEM
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "locale_prefs"
        private const val KEY_TAG = "selected_tag"
    }
}
