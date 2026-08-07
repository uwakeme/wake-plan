package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TimeSegment
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

/**
 * 时间段仓库
 */
interface SegmentRepository {

    /** 观察某计划的所有段（按 startMinutes 升序） */
    fun observeByPlan(planId: String): Flow<List<TimeSegment>>

    /** 观察所有段（跨 plan，按 startMinutes 升序）— Phase 2.4a 用 */
    fun observeAll(): Flow<List<TimeSegment>>

    /** 单次查询 */
    suspend fun getById(id: String): TimeSegment?

    /** 新建段 */
    suspend fun create(
        planId: String,
        title: String?,
        startMinutes: Int,
        endMinutes: Int,
        repeatRule: RepeatRule,
        customDays: Set<DayOfWeek> = emptySet(),
    ): TimeSegment

    /** 更新段（整个对象） */
    suspend fun update(segment: TimeSegment)

    /** 删除段（CASCADE 删任务） */
    suspend fun delete(id: String)
}
