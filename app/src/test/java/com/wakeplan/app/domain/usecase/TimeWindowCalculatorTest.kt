package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TimeSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * TimeWindowCalculator 单测 — Phase 2.1
 *
 * 覆盖：
 * - RepeatRule 4 个枚举分别处理
 * - 跨午夜（endMinutes > 1440）
 * - 段在 date 不生效时返回 null
 */
@DisplayName("TimeWindowCalculator")
class TimeWindowCalculatorTest {

    private val calculator = TimeWindowCalculator()
    private val monday = LocalDate.of(2026, 8, 10)  // 周一
    private val saturday = LocalDate.of(2026, 8, 15) // 周六

    @Nested
    @DisplayName("RepeatRule.ONCE")
    inner class OnceRule {
        @Test
        fun `ONCE 段 v0_1 临时按 DAILY 行为 — 任意日期都生效`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = "plan-1",
                title = "一次性",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.ONCE,
            )
            val window = calculator(segment, monday)
            assertNotNull(window)
            assertEquals(monday.atTime(9, 0), window!!.plannedStart)
            assertEquals(monday.atTime(10, 0), window.plannedEnd)
        }
    }

    @Nested
    @DisplayName("RepeatRule.DAILY")
    inner class DailyRule {
        @Test
        fun `DAILY 段在任意日期都生效`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = "plan-1",
                title = "每日晨练",
                startMinutes = 6 * 60,
                endMinutes = 7 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            listOf(monday, saturday).forEach { date ->
                val window = calculator(segment, date)
                assertNotNull(window, "$date 应生效")
                assertEquals(date.atTime(6, 0), window!!.plannedStart)
            }
        }
    }

    @Nested
    @DisplayName("RepeatRule.WEEKDAYS")
    inner class WeekdaysRule {
        private val segment = TimeSegment(
            id = "seg-1",
            planId = "plan-1",
            title = "工作日",
            startMinutes = 9 * 60,
            endMinutes = 18 * 60,
            repeatRule = RepeatRule.WEEKDAYS,
        )

        @Test
        fun `WEEKDAYS 段在周一生效`() {
            assertNotNull(calculator(segment, monday))
        }

        @Test
        fun `WEEKDAYS 段在周六返回 null`() {
            assertNull(calculator(segment, saturday))
        }

        @Test
        fun `WEEKDAYS 段在周日返回 null`() {
            val sunday = LocalDate.of(2026, 8, 16)
            assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
            assertNull(calculator(segment, sunday))
        }
    }

    @Nested
    @DisplayName("RepeatRule.CUSTOM")
    inner class CustomRule {
        @Test
        fun `CUSTOM 段在 customDays 内的日期生效`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = "plan-1",
                title = "周一三五",
                startMinutes = 19 * 60,
                endMinutes = 20 * 60,
                repeatRule = RepeatRule.CUSTOM,
                customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            )
            assertNotNull(calculator(segment, monday))
            assertNotNull(calculator(segment, LocalDate.of(2026, 8, 12))) // 周三
            assertNull(calculator(segment, LocalDate.of(2026, 8, 11)))    // 周二
        }

        @Test
        fun `CUSTOM 段 customDays 为空时所有日期都返回 null（防御性）`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = "plan-1",
                title = "空 customDays",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.CUSTOM,
                customDays = emptySet(),
            )
            assertNull(calculator(segment, monday))
        }
    }

    @Nested
    @DisplayName("跨午夜段")
    inner class CrossMidnight {
        @Test
        fun `endMinutes 大于 1440 时 plannedEnd 跨到次日`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = "plan-1",
                title = "夜班",
                startMinutes = 23 * 60,        // 1380
                endMinutes = 25 * 60,          // 1500 → 次日 01:00
                repeatRule = RepeatRule.DAILY,
            )
            val window = calculator(segment, monday)
            assertNotNull(window)
            assertEquals(monday.atTime(23, 0), window!!.plannedStart)
            assertEquals(monday.plusDays(1).atTime(1, 0), window.plannedEnd)
        }

        @Test
        fun `跨午夜段 plannedStart 在 DAILY 下每天都生效`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = "plan-1",
                title = "夜班",
                startMinutes = 22 * 60,
                endMinutes = 26 * 60,          // 次日 02:00
                repeatRule = RepeatRule.DAILY,
            )
            listOf(monday, saturday).forEach { date ->
                val window = calculator(segment, date)
                assertNotNull(window, "$date 应生效")
                assertEquals(date.atTime(22, 0), window!!.plannedStart)
                assertEquals(date.plusDays(1).atTime(2, 0), window.plannedEnd)
            }
        }
    }

    @Nested
    @DisplayName("边界时间")
    inner class Boundary {
        @Test
        fun `段正好跨午夜的整点(24_00)等于次日 00_00`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = "plan-1",
                title = "整点",
                startMinutes = 0,
                endMinutes = 24 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            val window = calculator(segment, monday)
            assertNotNull(window)
            assertEquals(monday.atStartOfDay(), window!!.plannedStart)
            assertEquals(monday.plusDays(1).atStartOfDay(), window.plannedEnd)
        }

        @Test
        fun `凌晨段(0_00-1_00)单天不跨日`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = "plan-1",
                title = "凌晨",
                startMinutes = 0,
                endMinutes = 60,
                repeatRule = RepeatRule.DAILY,
            )
            val window = calculator(segment, monday)
            assertNotNull(window)
            assertEquals(monday.atStartOfDay(), window!!.plannedStart)
            assertEquals(monday.atTime(1, 0), window.plannedEnd)
        }
    }
}
