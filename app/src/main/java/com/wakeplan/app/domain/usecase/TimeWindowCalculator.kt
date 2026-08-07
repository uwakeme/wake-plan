package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TimeSegment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 段在该天的实际时间窗口 — Phase 2 UseCase
 *
 * 跨午夜：endMinutes > 1440 表示延续到次日
 * 例：startMinutes=23*60=1380, endMinutes=25*60=1500
 *   → plannedStart=当天 23:00, plannedEnd=次日 01:00
 *   (LocalDateTime.plusMinutes 自动跨日)
 *
 * v0.1 ONCE 段按 DAILY 行为处理：每天都生成 PENDING；
 * v0.2 引入 scheduledDate 字段时升级（详见 docs/plan/00-roadmap.md）
 */
data class PlannedWindow(
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
)

class TimeWindowCalculator @Inject constructor() {

    /**
     * @return 该段在 date 的窗口；如果段在 date 不生效（如 WEEKDAYS 段在周末），返回 null
     */
    operator fun invoke(segment: TimeSegment, date: LocalDate): PlannedWindow? {
        if (!isActiveOn(segment, date)) return null
        return PlannedWindow(
            plannedStart = date.atStartOfDay().plusMinutes(segment.startMinutes.toLong()),
            plannedEnd = date.atStartOfDay().plusMinutes(segment.endMinutes.toLong()),
        )
    }

    private fun isActiveOn(segment: TimeSegment, date: LocalDate): Boolean =
        when (segment.repeatRule) {
            // v0.1 临时：ONCE 段按 DAILY 处理（用户用 SKIPPED 标记非目标日）
            // v0.2 引入 scheduledDate 字段后改写
            RepeatRule.ONCE -> true
            RepeatRule.WEEKDAYS -> date.dayOfWeek in WEEKDAYS
            RepeatRule.DAILY -> true
            RepeatRule.CUSTOM -> segment.customDays.isNotEmpty() && date.dayOfWeek in segment.customDays
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
