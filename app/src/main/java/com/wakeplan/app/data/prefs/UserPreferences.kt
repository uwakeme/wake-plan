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
    }
}
