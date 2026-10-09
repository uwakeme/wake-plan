package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 链式碰撞延期 UseCase — v0.4.1 重写
 *
 * 规则（对齐 PRD AC-06）：
 * 1. 按 planId 分组独立计算——A 计划的延期不传播到 B 计划（AC-06.5）
 * 2. 每个 plan 内只处理 scheduledDate == today 的当天实例，按 computedStartAt 升序
 * 3. 相邻对 (prev, cur) 从前往后扫：
 *    - prevTask.isLocked → 链断：cur 及之后都不被推（AC-06.2）
 *    - prev SKIPPED → 不推下游（用户主动放弃，释放了时段）
 *    - prev COMPLETED → 以 actualEnd 为准推下游（晚完成才推：actualEnd > cur 开始
 *      才产生 delta；按时/提前完成不推也不拉）——AC-06.1 的 overrun 即来源于此
 *    - prev IN_PROGRESS → 以 max(plannedEnd, now) 为准推下游（进行中拖堂实时可见）
 *    - cur 已终态（COMPLETED/SKIPPED）→ 不动
 *    - curTask.isLocked → 防御性：cur 不动，下一轮会在 cur 处断
 *    - prevEnd > curStart 时：cur.plannedStart/End += delta，affectedTasks += prev.taskId
 * 4. 非当天的 completion 原样返回（昨天跨午夜拖到今天的尾巴不参与当天链）
 *
 * 结果幂等：调用方把调整后的窗口落库后重跑本函数，不会产生二次位移
 * （cur 已在 prevEnd 之后，条件不再成立）。
 */
class CascadeDelayCalculator @Inject constructor() {

    /**
     * @return 调整后的 completions 新列表（输入不被修改）
     */
    operator fun invoke(
        completions: List<TaskCompletion>,
        tasks: List<Task>,
        today: LocalDate,
        now: LocalDateTime,
    ): List<TaskCompletion> {
        if (completions.isEmpty()) return emptyList()

        val taskMap = tasks.associateBy { it.id }

        // 1. 拆当天 + 其它日期（昨天的跨午夜尾巴原样返回）
        val (otherDay, todayList) = completions.partition { it.scheduledDate != today }
        if (todayList.isEmpty()) return completions

        // 2. 按 plan 分组，各组独立链式（跨 plan 不传播）
        val adjustedByPlan = todayList
            .groupBy { it.planId }
            .map { (planId, planCompletions) ->
                val sorted = planCompletions
                    .sortedBy { it.computedStartAt() }
                    .toMutableList()
                scanChain(sorted, taskMap, now)
                planId to sorted
            }

        return otherDay + adjustedByPlan.flatMap { it.second }
    }

    private fun scanChain(
        sorted: MutableList<TaskCompletion>,
        taskMap: Map<String, Task>,
        now: LocalDateTime,
    ) {
        for (i in 0 until sorted.lastIndex) {
            val prev = sorted[i]
            val cur = sorted[i + 1]
            val prevTask = taskMap[prev.taskId] ?: continue
            val curTask = taskMap[cur.taskId] ?: continue

            // 钉死：链断
            if (prevTask.isLocked) break

            // 跳过的 prev 不推下游（释放了时段）
            if (prev.status == TaskStatus.SKIPPED) continue
            // 终态 cur 不被推
            if (cur.status == TaskStatus.COMPLETED || cur.status == TaskStatus.SKIPPED) continue
            // 钉死 cur：cur 不被推（链下一轮会在 cur 处自然断）
            if (curTask.isLocked) continue

            val prevEnd = prev.computedEndAt(now)
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
    }

    private fun TaskCompletion.computedStartAt(): LocalDateTime =
        actualStart ?: plannedStart

    /**
     * prev 的"有效结束时间"：
     * - COMPLETED 用 actualEnd（晚完成即 overrun 来源，AC-06.1）
     * - IN_PROGRESS 用 max(plannedEnd, actualEnd?, now)：拖堂中实时可见；
     *   actualEnd 已落库时（如补账数据）以它为准
     * - PENDING / SKIPPED 用 plannedEnd（按计划算）
     */
    private fun TaskCompletion.computedEndAt(now: LocalDateTime): LocalDateTime = when (status) {
        TaskStatus.COMPLETED -> actualEnd ?: plannedEnd
        TaskStatus.IN_PROGRESS -> maxOf(plannedEnd, actualEnd ?: plannedEnd, now)
        TaskStatus.PENDING, TaskStatus.SKIPPED -> plannedEnd
    }
}
