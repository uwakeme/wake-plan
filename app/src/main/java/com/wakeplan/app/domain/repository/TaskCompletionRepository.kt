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

    fun observeByPlanAndDate(planId: String, date: LocalDate): Flow<List<TaskCompletion>>

    suspend fun getById(id: String): TaskCompletion?

    suspend fun upsert(completion: TaskCompletion)

    /** 日期范围一次性查询 — Phase 5.2 统计用 */
    suspend fun getByDateRange(start: LocalDate, end: LocalDate): List<TaskCompletion>
}
