package com.wakeplan.app.data.db

import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库初始化 seed
 * - 5.3 决策：3 个内置模板
 * - 幂等标记存 DataStore（v0.4.1）：只 seed 一次——老逻辑按"表空"判断，
 *   用户删光所有模板后每次启动都会被复活
 * - 启动时由 App.onCreate 异步调用
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val templateDao: PlanTemplateDao,
    private val preferences: UserPreferences,
) {
    fun seedIfEmpty(applicationScope: CoroutineScope) {
        applicationScope.launch(Dispatchers.IO) {
            if (preferences.databaseSeeded.first()) return@launch
            val templateCount = templateDao.countAll()
            if (templateCount == 0) {
                BUILT_IN_TEMPLATES.forEach { templateDao.insert(it) }
            }
            preferences.setDatabaseSeeded(true)
        }
    }

    companion object {
        private val BUILT_IN_TEMPLATES = listOf(
            PlanTemplateEntity(
                id = "tpl_morning",
                title = "标准晨练",
                colorHex = "#C48520",  // 晨光金
                isBuiltIn = true,
            ),
            PlanTemplateEntity(
                id = "tpl_study",
                title = "标准学习",
                colorHex = "#3B6FB5",  // 海蓝
                isBuiltIn = true,
            ),
            PlanTemplateEntity(
                id = "tpl_weekend_reading",
                title = "周末阅读",
                colorHex = "#8B5CF6",  // 紫罗兰
                isBuiltIn = true,
            ),
        )
    }
}
