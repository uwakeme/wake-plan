package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.TaskCompletion
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 任务执行实例仓库 — Phase 2 实装
 * Phase 1.1 仅暴露接口，不被任何 UI 调用
 */
interface TaskCompletionRepository {

    fun observeByDate(date: LocalDate): Flow<List<TaskCompletion>>

    /** 闭区间 [start, end] 范围观察 — 跨午夜跨日查询用（含昨天的跨午夜尾巴） */
    fun observeByDateRangeInclusive(start: LocalDate, end: LocalDate): Flow<List<TaskCompletion>>

    fun observeByPlanAndDate(planId: String, date: LocalDate): Flow<List<TaskCompletion>>

    suspend fun getById(id: String): TaskCompletion?

    suspend fun upsert(completion: TaskCompletion)

    /**
     * 仅当 (taskId, scheduledDate) 不存在时插入 — 铺当天 PENDING 实例专用，
     * 不会覆盖已有行（保住真实打卡数据与已挂闹钟的 id）
     */
    suspend fun insertIfAbsent(completion: TaskCompletion)

    suspend fun delete(id: String)

    /** 日期范围一次性查询 — Phase 5.2 统计用 */
    suspend fun getByDateRange(start: LocalDate, end: LocalDate): List<TaskCompletion>

    /** 全量一次性查询 — v0.5 CSV 导出用（与备份导出同假设：数据量 < 1k 行级） */
    suspend fun getAll(): List<TaskCompletion>
}
