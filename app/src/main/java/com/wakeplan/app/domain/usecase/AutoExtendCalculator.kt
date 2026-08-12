package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 自动延期 UseCase — Phase 2.2
 *
 * 规则（v0.1，无 undo）：
 * 1. 按 plannedStart 升序遍历
 * 2. 遇到 IN_PROGRESS：算 overrun = max(0, now - plannedEnd)，累加 currentOffset
 *    - currentAffectors 记录所有让 currentOffset 增长的 IN_PROGRESS taskId
 * 3. 遇到 PENDING：
 *    - 若对应 task 不存在 / isLocked=true / autoExtend=false → 不动
 *    - 若 currentOffset > 0 → plannedStart/End += currentOffset，affectedTasks = currentAffectors
 * 4. 遇到 COMPLETED / SKIPPED → 不动（终态）
 *
 * 跨午夜：`LocalDateTime.plusMinutes` 自动跨日，无需特殊处理
 *
 * 撤销（undo）v0.2 再做 — 见 docs/plan/00-roadmap.md 决策 #6
 */
class AutoExtendCalculator @Inject constructor() {

    /**
     * @return 调整后的 completions 列表（新 list，输入不被修改）
     */
    operator fun invoke(
        completions: List<TaskCompletion>,
        tasks: List<Task>,
        now: LocalDateTime,
    ): List<TaskCompletion> {
        if (completions.isEmpty()) return emptyList()

        val sorted = completions.sortedBy { it.plannedStart }
        val taskMap = tasks.associateBy { it.id }

        var currentOffsetMinutes = 0L
        var currentAffectors: List<String> = emptyList()
        val result = ArrayList<TaskCompletion>(sorted.size)

        for (c in sorted) {
            when (c.status) {
                TaskStatus.IN_PROGRESS -> {
                    val overrun = Duration.between(c.plannedEnd, now).toMinutes().coerceAtLeast(0L)
                    if (overrun > 0) {
                        currentOffsetMinutes += overrun
                        currentAffectors = currentAffectors + c.taskId
                    }
                    result.add(c.copy(overrunMinutes = overrun.toInt()))
                }
                TaskStatus.PENDING -> {
                    val task = taskMap[c.taskId]
                    // v0.3 砍 autoExtend 字段：所有任务都参与延期（"不参与"靠 isLocked=true 表达）
                    val shouldExtend = currentOffsetMinutes > 0
                        && task != null
                        && !task.isLocked
                    if (shouldExtend) {
                        result.add(
                            c.copy(
                                plannedStart = c.plannedStart.plusMinutes(currentOffsetMinutes),
                                plannedEnd = c.plannedEnd.plusMinutes(currentOffsetMinutes),
                                affectedTasks = currentAffectors,
                            )
                        )
                    } else {
                        result.add(c)
                    }
                }
                TaskStatus.COMPLETED, TaskStatus.SKIPPED -> {
                    // 终态不动；currentOffset 仍由 IN_PROGRESS 维持
                    result.add(c)
                }
            }
        }
        return result
    }
}
