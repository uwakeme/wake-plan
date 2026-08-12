package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 链式碰撞延期 UseCase — v0.3 替代 v0.2 的 AutoExtendCalculator
 *
 * 规则（v0.3，用户拍板）：
 * 1. 只处理 scheduledDate == today 的当天实例
 * 2. 按 computedStartAt 升序（actualStart ?: plannedStart）
 * 3. 从前往后扫相邻对 (prev, cur)：
 *    - prev.isLocked == true → 链断，cur 不被推，cur 之后也不被推
 *    - prev.status ∈ {COMPLETED, SKIPPED} → 不动（终态）
 *    - cur.status  ∈ {COMPLETED, SKIPPED} → 不动（终态）
 *    - cur.isLocked == true → 防御性：cur 不动，链下一轮会在 cur 处断
 *    - prev.computedEndAt > cur.computedStartAt：
 *      - cur.plannedStart += delta
 *      - cur.plannedEnd   += delta
 *      - cur.affectedTasks += prev.taskId
 * 4. 非当天的 completion 原样返回（不影响明天 / 昨天）
 *
 * 与 v0.2 AutoExtendCalculator 区别：
 * - v0.2: currentOffset 累加（多个 IN_PROGRESS 累加），triggered by now
 * - v0.3: 单点链式碰撞，triggered by actualEnd（当 prev 还在 IN_PROGRESS 时
 *         actualEnd 通常 = now 或已落库的实际结束时间）
 * - v0.2: isLocked 仅"不参与延期"，但 currentOffset 仍传给后续
 * - v0.3: isLocked 是"钉死"，锁之后的链直接断
 */
class CascadeDelayCalculator @Inject constructor() {

    /**
     * @return 调整后的 completions 新列表（输入不被修改）
     */
    operator fun invoke(
        completions: List<TaskCompletion>,
        tasks: List<Task>,
        today: LocalDate,
        @Suppress("UNUSED_PARAMETER") now: LocalDateTime,
    ): List<TaskCompletion> {
        if (completions.isEmpty()) return emptyList()

        val taskMap = tasks.associateBy { it.id }

        // 1. 拆当天 + 其它日期
        val (otherDay, todayList) = completions.partition { it.scheduledDate != today }
        if (todayList.isEmpty()) return completions

        // 2. 按 computedStartAt 升序（实际开始时间优先，fallback 到计划开始）
        val sorted = todayList.sortedBy { it.computedStartAt() }.toMutableList()

        // 3. 相邻对链式扫描
        for (i in 0 until sorted.lastIndex) {
            val prev = sorted[i]
            val cur = sorted[i + 1]
            val prevTask = taskMap[prev.taskId] ?: continue
            val curTask = taskMap[cur.taskId] ?: continue

            // 钉死：链断
            if (prevTask.isLocked) break

            // 终态 prev 不推下游
            if (prev.status == TaskStatus.COMPLETED || prev.status == TaskStatus.SKIPPED) continue
            // 终态 cur 不被推
            if (cur.status == TaskStatus.COMPLETED || cur.status == TaskStatus.SKIPPED) continue
            // 钉死 cur：cur 不被推（链下一轮会在 cur 处自然断）
            if (curTask.isLocked) continue

            val prevEnd = prev.computedEndAt()
            val curStart = cur.computedStartAt()
            if (prevEnd.isAfter(curStart)) {
                val delta = Duration.between(curStart, prevEnd)
                sorted[i + 1] = cur.copy(
                    plannedStart = cur.plannedStart.plus(delta),
                    plannedEnd = cur.plannedEnd.plus(delta),
                    affectedTasks = cur.affectedTasks + prev.taskId,
                )
            }
        }

        return otherDay + sorted
    }

    private fun TaskCompletion.computedStartAt(): LocalDateTime =
        actualStart ?: plannedStart

    private fun TaskCompletion.computedEndAt(): LocalDateTime =
        actualEnd ?: plannedEnd
}
