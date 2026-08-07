package com.wakeplan.app.domain.model

import java.time.DayOfWeek

/**
 * 时间段 Domain Model
 * startMinutes / endMinutes 用当天分钟数（0..1440），跨日允许 endMinutes > 1440
 * 跨午夜由 TimeWindowCalculator（Phase 2）折算
 */
data class TimeSegment(
    val id: String,
    val planId: String,
    val title: String?,
    val startMinutes: Int,
    val endMinutes: Int,
    val repeatRule: RepeatRule,
    val customDays: Set<DayOfWeek> = emptySet(),
) {
    val durationMinutes: Int get() = endMinutes - startMinutes
}
