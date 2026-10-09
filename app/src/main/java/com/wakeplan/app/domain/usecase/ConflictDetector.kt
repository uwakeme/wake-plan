package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 任务时段冲突检测 — Phase 2.3，v0.4.1 支持跨午夜跨日
 *
 * 用途：TodayScreen 当前活跃卡片标红 / 24h 时间线冲突高亮
 *
 * 规则：
 * - 半开区间 [start, end)：a.end == b.start 不算重叠
 * - 跨 plan：合并所有 plan 的任务，统一查冲突
 * - SKIPPED 不参与（用户主动跳过，不警告）
 * - COMPLETED / IN_PROGRESS / PENDING 都参与（已经完成的撞了也要标红告诉用户"刚才撞了"）
 * - 跨日边界：按"时间窗与 [date, date+1) 相交"筛选候选——昨天 23:00–今天 00:30 的
 *   跨午夜尾巴与今天 00:00–01:00 的任务也判重叠（v0.4.1 前只比 scheduledDate，永远漏掉）
 */
data class TaskConflict(
    val taskA: TaskCompletion,
    val taskB: TaskCompletion,
    val overlapStart: LocalDateTime,
    val overlapEnd: LocalDateTime,
) {
    /** 重叠分钟数（向下取整） */
    val overlapMinutes: Int
        get() = (java.time.Duration.between(overlapStart, overlapEnd).toMinutes()).toInt()
}

class ConflictDetector @Inject constructor() {

    /**
     * @param completions 跨 plan 跨日的所有 completion 快照
     * @param date 只看当天（其他日不参与）
     * @return 所有任务对之间的冲突列表（无序对：a-b 与 b-a 视为同一对，只返一次）
     */
    operator fun invoke(
        completions: List<TaskCompletion>,
        date: LocalDate,
    ): List<TaskConflict> {
        val dayStart = date.atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val candidates = completions
            .filter {
                it.status != TaskStatus.SKIPPED &&
                    it.plannedEnd.isAfter(dayStart) && it.plannedStart.isBefore(dayEnd)
            }
            .sortedBy { it.plannedStart }

        if (candidates.size < 2) return emptyList()

        val conflicts = mutableListOf<TaskConflict>()
        for (i in candidates.indices) {
            val a = candidates[i]
            // 优化：b.plannedStart >= a.plannedEnd 时后续 b 都不可能跟 a 重叠
            for (j in (i + 1) until candidates.size) {
                val b = candidates[j]
                if (!b.plannedStart.isBefore(a.plannedEnd)) break  // a 跟 b 不可能重叠，b 后面的更不可能
                val overlapStart = if (a.plannedStart.isAfter(b.plannedStart)) a.plannedStart else b.plannedStart
                val overlapEnd = if (a.plannedEnd.isBefore(b.plannedEnd)) a.plannedEnd else b.plannedEnd
                if (overlapStart.isBefore(overlapEnd)) {
                    conflicts.add(TaskConflict(a, b, overlapStart, overlapEnd))
                }
            }
        }
        return conflicts
    }
}
