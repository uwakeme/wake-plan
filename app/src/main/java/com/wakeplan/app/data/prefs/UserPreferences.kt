package com.wakeplan.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户偏好 — DataStore 包装
 *
 * 通知相关（PRD US-10）：
 * - `taskStartingLeadMinutes` 默认 5（5.3 决策 #2）
 * - `taskStartingEnabled` / `taskOverdueEnabled` 默认 true
 * - `taskCompletedEnabled` / `planRecapEnabled` 默认 false（v0.1 不实现 P2 渠道，留 settings 入口）
 *
 * Settings 屏和 NotificationScheduler 都从这里读
 */
@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    // ===== 通知偏好 =====
    val taskStartingLeadMinutes: Flow<Int> = dataStore.data
        .map { it[KEY_TASK_STARTING_LEAD] ?: DEFAULT_LEAD_MINUTES }

    val taskStartingEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_TASK_STARTING_ENABLED] ?: true }

    val taskOverdueEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_TASK_OVERDUE_ENABLED] ?: true }

    val taskCompletedEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_TASK_COMPLETED_ENABLED] ?: false }

    val planRecapEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_PLAN_RECAP_ENABLED] ?: false }

    /** 首次启动引导页是否已完成 — Phase 3.3 */
    val hasShownOnboarding: Flow<Boolean> = dataStore.data
        .map { it[KEY_HAS_SHOWN_ONBOARDING] ?: false }

    /** 内置模板是否已 seed 过（幂等标记，防止用户删光模板后被反复复活） */
    val databaseSeeded: Flow<Boolean> = dataStore.data
        .map { it[KEY_DATABASE_SEEDED] ?: false }

    /**
     * 统计记录保留天数（v0.5 阶段六）：0 = 永久（默认），90 / 365 可选。
     * 只影响 COMPLETED/SKIPPED 的超龄行，进行中/未开始永不动
     */
    val completionRetentionDays: Flow<Int> = dataStore.data
        .map { it[KEY_COMPLETION_RETENTION_DAYS] ?: 0 }

    // ===== 专注计时参数（v0.6 番茄钟）=====

    val focusMinutes: Flow<Int> = dataStore.data
        .map { it[KEY_FOCUS_MINUTES] ?: FOCUS_MINUTES_DEFAULT }

    val focusShortBreakMinutes: Flow<Int> = dataStore.data
        .map { it[KEY_FOCUS_SHORT_BREAK] ?: FOCUS_SHORT_BREAK_DEFAULT }

    val focusLongBreakMinutes: Flow<Int> = dataStore.data
        .map { it[KEY_FOCUS_LONG_BREAK] ?: FOCUS_LONG_BREAK_DEFAULT }

    /** 阶段结束后自动开始下一阶段 */
    val focusAutoStartNext: Flow<Boolean> = dataStore.data
        .map { it[KEY_FOCUS_AUTO_NEXT] ?: false }

    suspend fun setTaskStartingLeadMinutes(minutes: Int) {
        require(minutes in ALLOWED_LEAD_VALUES) { "lead minutes must be in $ALLOWED_LEAD_VALUES" }
        dataStore.edit { it[KEY_TASK_STARTING_LEAD] = minutes }
    }

    suspend fun setTaskStartingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TASK_STARTING_ENABLED] = enabled }
    }

    suspend fun setTaskOverdueEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TASK_OVERDUE_ENABLED] = enabled }
    }

    suspend fun setTaskCompletedEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TASK_COMPLETED_ENABLED] = enabled }
    }

    suspend fun setPlanRecapEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PLAN_RECAP_ENABLED] = enabled }
    }

    suspend fun setHasShownOnboarding(shown: Boolean) {
        dataStore.edit { it[KEY_HAS_SHOWN_ONBOARDING] = shown }
    }

    suspend fun setDatabaseSeeded(seeded: Boolean) {
        dataStore.edit { it[KEY_DATABASE_SEEDED] = seeded }
    }

    suspend fun setCompletionRetentionDays(days: Int) {
        require(days in ALLOWED_RETENTION_DAYS) { "retention days must be in $ALLOWED_RETENTION_DAYS" }
        dataStore.edit { it[KEY_COMPLETION_RETENTION_DAYS] = days }
    }

    suspend fun setFocusMinutes(minutes: Int) {
        require(minutes in FOCUS_MINUTES_ALLOWED) { "focus minutes must be in $FOCUS_MINUTES_ALLOWED" }
        dataStore.edit { it[KEY_FOCUS_MINUTES] = minutes }
    }

    suspend fun setFocusShortBreakMinutes(minutes: Int) {
        require(minutes in FOCUS_SHORT_BREAK_ALLOWED) { "short break must be in $FOCUS_SHORT_BREAK_ALLOWED" }
        dataStore.edit { it[KEY_FOCUS_SHORT_BREAK] = minutes }
    }

    suspend fun setFocusLongBreakMinutes(minutes: Int) {
        require(minutes in FOCUS_LONG_BREAK_ALLOWED) { "long break must be in $FOCUS_LONG_BREAK_ALLOWED" }
        dataStore.edit { it[KEY_FOCUS_LONG_BREAK] = minutes }
    }

    suspend fun setFocusAutoStartNext(enabled: Boolean) {
        dataStore.edit { it[KEY_FOCUS_AUTO_NEXT] = enabled }
    }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 5
        val ALLOWED_LEAD_VALUES = setOf(0, 5, 10, 15)

        val KEY_TASK_STARTING_LEAD = intPreferencesKey("task_starting_lead_minutes")
        val KEY_TASK_STARTING_ENABLED = booleanPreferencesKey("task_starting_enabled")
        val KEY_TASK_OVERDUE_ENABLED = booleanPreferencesKey("task_overdue_enabled")
        val KEY_TASK_COMPLETED_ENABLED = booleanPreferencesKey("task_completed_enabled")
        val KEY_PLAN_RECAP_ENABLED = booleanPreferencesKey("plan_recap_enabled")
        val KEY_HAS_SHOWN_ONBOARDING = booleanPreferencesKey("has_shown_onboarding")
        val KEY_DATABASE_SEEDED = booleanPreferencesKey("database_seeded")
        val KEY_COMPLETION_RETENTION_DAYS = intPreferencesKey("completion_retention_days")

        /** 数据保留选项：0 = 永久（默认） */
        val ALLOWED_RETENTION_DAYS = setOf(0, 90, 365)

        // ===== 专注参数（v0.6；与 FocusConfig 默认值一致）=====
        val KEY_FOCUS_MINUTES = intPreferencesKey("focus_minutes")
        val KEY_FOCUS_SHORT_BREAK = intPreferencesKey("focus_short_break_minutes")
        val KEY_FOCUS_LONG_BREAK = intPreferencesKey("focus_long_break_minutes")
        val KEY_FOCUS_AUTO_NEXT = booleanPreferencesKey("focus_auto_start_next")

        const val FOCUS_MINUTES_DEFAULT = 25
        const val FOCUS_SHORT_BREAK_DEFAULT = 5
        const val FOCUS_LONG_BREAK_DEFAULT = 15
        val FOCUS_MINUTES_ALLOWED = setOf(15, 25, 45, 60)
        val FOCUS_SHORT_BREAK_ALLOWED = setOf(3, 5, 10)
        val FOCUS_LONG_BREAK_ALLOWED = setOf(15, 30)
    }
}
