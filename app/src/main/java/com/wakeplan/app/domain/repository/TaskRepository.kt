package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

/**
 * 任务仓库 — v0.3 扁平化
 *
 * 任务直接挂在 plan 下，自带 startMinutes / endMinutes / repeatRule。
 * 不再有 segment 维度。
 */
interface TaskRepository {

    /** 观察某计划下所有任务（按 startMinutes 升序） */
    fun observeByPlan(planId: String): Flow<List<Task>>

    /** 观察所有任务（跨 plan，按 startMinutes 升序）— Today 时间线 / 冲突检测用 */
    fun observeAll(): Flow<List<Task>>

    /** 单次查询 */
    suspend fun getById(id: String): Task?

    /** 新建任务，自动追加到 plan 末尾（orderInPlan = max + 1） */
    suspend fun create(
        planId: String,
        title: String,
        startMinutes: Int,
        endMinutes: Int,
        repeatRule: RepeatRule,
        customDays: Set<DayOfWeek> = emptySet(),
        isLocked: Boolean = false,
        note: String? = null,
    ): Task

    /** 更新任务（整个对象） */
    suspend fun update(task: Task)

    /** 删除任务 */
    suspend fun delete(id: String)

    /** 重排 plan 内任务（orderedTaskIds 是新顺序） */
    suspend fun reorder(planId: String, orderedTaskIds: List<String>)
}
