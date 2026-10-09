package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 任务在该天的实际时间窗口 — v0.3 扁平化
 *
 * v0.3 砍段后，task 自带 startMinutes / endMinutes / repeatRule / customDays。
 * 跨午夜：endMinutes > 1440 表示延续到次日（例 startMinutes=23*60, endMinutes=25*60
 *   → plannedStart=当天 23:00, plannedEnd=次日 01:00；LocalDateTime.plusMinutes 自动跨日）
 *
 * v0.4.1 起 ONCE 按 PRD AC-02.2 处理：只在 createdEpochDay 当天生效（旧行退化为 DAILY）。
 */
data class PlannedWindow(
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
)

class TaskWindowCalculator @Inject constructor() {

    /**
     * @return 该 task 在 date 的窗口；如果 task 在 date 不生效（如 WEEKDAYS task 在周末），返回 null
     */
    operator fun invoke(task: Task, date: LocalDate): PlannedWindow? {
        if (!isActiveOn(task, date)) return null
        return PlannedWindow(
            plannedStart = date.atStartOfDay().plusMinutes(task.startMinutes.toLong()),
            plannedEnd = date.atStartOfDay().plusMinutes(task.endMinutes.toLong()),
        )
    }

    private fun isActiveOn(task: Task, date: LocalDate): Boolean =
        when (task.repeatRule) {
            // ONCE 只在建任务那天生效；createdEpochDay=0 是 schema v3 迁移旧行，
            // 找不回原始创建日，保持旧版"每天"行为兜底（v0.4.1 前建的 ONCE 任务不受影响）
            RepeatRule.ONCE -> task.createdEpochDay == 0L || task.createdEpochDay == date.toEpochDay()
            RepeatRule.WEEKDAYS -> date.dayOfWeek in WEEKDAYS
            RepeatRule.DAILY -> true
            RepeatRule.CUSTOM -> task.customDays.isNotEmpty() && date.dayOfWeek in task.customDays
        }

    companion object {
        private val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}
