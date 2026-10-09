package com.wakeplan.app.data.db

import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.prefs.UserPreferences
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 统计记录保留清理 — v0.5 阶段六
 *
 * task_completions 每天每任务一行且无清理机制（审查遗留项）。冷启时按用户配置
 * 删除终态（COMPLETED/SKIPPED）超龄行；默认 0 = 永久，不删任何数据。
 * 进行中/未开始的实例永不受影响。
 */
@Singleton
class CompletionRetentionCleaner @Inject constructor(
    private val preferences: UserPreferences,
    private val completionDao: TaskCompletionDao,
    private val clock: Clock,
) {
    /** 读配置并清理；返回删除行数（0 = 未配置或无可删） */
    suspend fun cleanIfConfigured(): Int {
        val days = preferences.completionRetentionDays.first()
        if (days <= 0) return 0
        return completionDao.deleteTerminalOlderThan(cutoffFor(days))
    }

    /** 截止日 = 今天 - N 天（纯函数便于单测） */
    fun cutoffFor(days: Int): LocalDate = LocalDate.now(clock).minusDays(days.toLong())
}
