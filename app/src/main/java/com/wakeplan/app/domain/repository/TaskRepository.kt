package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * 任务仓库
 */
interface TaskRepository {

    /** 观察某段内的所有任务（按 orderInSegment 升序） */
    fun observeBySegment(segmentId: String): Flow<List<Task>>

    /** 观察某计划下所有段的任务（join，用于冲突检测 / 时间线） */
    fun observeByPlan(planId: String): Flow<List<Task>>

    /** 观察所有任务（跨 plan 跨段）— Phase 2.4a 用 */
    fun observeAll(): Flow<List<Task>>

    /** 单次查询 */
    suspend fun getById(id: String): Task?

    /** 新建任务，自动追加到段尾（orderInSegment = max + 1） */
    suspend fun create(
        segmentId: String,
        title: String,
        plannedDurationMinutes: Int,
        autoExtend: Boolean = true,
        isLocked: Boolean = false,
        note: String? = null,
    ): Task

    /** 更新任务（整个对象） */
    suspend fun update(task: Task)

    /** 删除任务 */
    suspend fun delete(id: String)

    /** 重排段内任务（orderedTaskIds 是新顺序） */
    suspend fun reorder(segmentId: String, orderedTaskIds: List<String>)
}
