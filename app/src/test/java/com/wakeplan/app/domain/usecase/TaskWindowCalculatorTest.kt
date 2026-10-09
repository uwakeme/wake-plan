package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * TaskWindowCalculator 单测 — v0.3 扁平化
 *
 * v0.3 砍段后，task 自带 startMinutes / endMinutes / repeatRule / customDays。
 * 覆盖：
 * - RepeatRule 4 个枚举分别处理
 * - 跨午夜（endMinutes > 1440）
 * - task 在 date 不生效时返回 null
 */
@DisplayName("TaskWindowCalculator")
class TaskWindowCalculatorTest {

    private val calculator = TaskWindowCalculator()
    private val monday = LocalDate.of(2026, 8, 10)  // 周一
    private val saturday = LocalDate.of(2026, 8, 15) // 周六

    @Nested
    @DisplayName("RepeatRule.ONCE")
    inner class OnceRule {
        @Test
        fun `ONCE 只在 createdEpochDay 当天生效 — 其他日期不生成实例（v0_4_1 修正）`() {
            val createdDay = monday.toEpochDay()
            val task = taskOf(
                id = "t-1",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.ONCE,
                createdEpochDay = createdDay,
            )
            val window = calculator(task, monday)
            assertNotNull(window)
            assertEquals(monday.atTime(9, 0), window!!.plannedStart)
            assertEquals(monday.atTime(10, 0), window.plannedEnd)
            // 第二天/上周都不再生成（快速录入的临时任务不复活）
            assertNull(calculator(task, monday.plusDays(1)))
            assertNull(calculator(task, monday.minusDays(7)))
        }

        @Test
        fun `ONCE 旧数据兼容 — createdEpochDay=0 的迁移行退化为每天生效`() {
            val task = taskOf(
                id = "t-legacy",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.ONCE,
                createdEpochDay = 0L,
            )
            listOf(monday, saturday).forEach { date ->
                assertNotNull(calculator(task, date), "旧行 $date 应保持每天生效")
            }
        }
    }

    @Nested
    @DisplayName("RepeatRule.DAILY")
    inner class DailyRule {
        @Test
        fun `DAILY task 在任意日期都生效`() {
            val task = taskOf(
                id = "t-1",
                startMinutes = 6 * 60,
                endMinutes = 7 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            listOf(monday, saturday).forEach { date ->
                val window = calculator(task, date)
                assertNotNull(window, "$date 应生效")
                assertEquals(date.atTime(6, 0), window!!.plannedStart)
            }
        }
    }

    @Nested
    @DisplayName("RepeatRule.WEEKDAYS")
    inner class WeekdaysRule {
        private val task = taskOf(
            id = "t-1",
            startMinutes = 9 * 60,
            endMinutes = 18 * 60,
            repeatRule = RepeatRule.WEEKDAYS,
        )

        @Test
        fun `WEEKDAYS task 在周一生效`() {
            assertNotNull(calculator(task, monday))
        }

        @Test
        fun `WEEKDAYS task 在周六返回 null`() {
            assertNull(calculator(task, saturday))
        }

        @Test
        fun `WEEKDAYS task 在周日返回 null`() {
            val sunday = LocalDate.of(2026, 8, 16)
            assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
            assertNull(calculator(task, sunday))
        }
    }

    @Nested
    @DisplayName("RepeatRule.CUSTOM")
    inner class CustomRule {
        @Test
        fun `CUSTOM task 在 customDays 内的日期生效`() {
            val task = taskOf(
                id = "t-1",
                startMinutes = 19 * 60,
                endMinutes = 20 * 60,
                repeatRule = RepeatRule.CUSTOM,
                customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            )
            assertNotNull(calculator(task, monday))
            assertNotNull(calculator(task, LocalDate.of(2026, 8, 12))) // 周三
            assertNull(calculator(task, LocalDate.of(2026, 8, 11)))    // 周二
        }

        @Test
        fun `CUSTOM task customDays 为空时所有日期都返回 null（防御性）`() {
            val task = taskOf(
                id = "t-1",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.CUSTOM,
                customDays = emptySet(),
            )
            assertNull(calculator(task, monday))
        }
    }

    @Nested
    @DisplayName("跨午夜")
    inner class CrossMidnight {
        @Test
        fun `endMinutes 大于 1440 时 plannedEnd 跨到次日`() {
            val task = taskOf(
                id = "t-1",
                startMinutes = 23 * 60,        // 1380
                endMinutes = 25 * 60,          // 1500 → 次日 01:00
                repeatRule = RepeatRule.DAILY,
            )
            val window = calculator(task, monday)
            assertNotNull(window)
            assertEquals(monday.atTime(23, 0), window!!.plannedStart)
            assertEquals(monday.plusDays(1).atTime(1, 0), window.plannedEnd)
        }

        @Test
        fun `跨午夜 task 在 DAILY 下每天都生效`() {
            val task = taskOf(
                id = "t-1",
                startMinutes = 22 * 60,
                endMinutes = 26 * 60,          // 次日 02:00
                repeatRule = RepeatRule.DAILY,
            )
            listOf(monday, saturday).forEach { date ->
                val window = calculator(task, date)
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
        fun `task 正好跨午夜的整点(24_00)等于次日 00_00`() {
            val task = taskOf(
                id = "t-1",
                startMinutes = 0,
                endMinutes = 24 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            val window = calculator(task, monday)
            assertNotNull(window)
            assertEquals(monday.atStartOfDay(), window!!.plannedStart)
            assertEquals(monday.plusDays(1).atStartOfDay(), window.plannedEnd)
        }

        @Test
        fun `凌晨 task(0_00-1_00)单天不跨日`() {
            val task = taskOf(
                id = "t-1",
                startMinutes = 0,
                endMinutes = 60,
                repeatRule = RepeatRule.DAILY,
            )
            val window = calculator(task, monday)
            assertNotNull(window)
            assertEquals(monday.atStartOfDay(), window!!.plannedStart)
            assertEquals(monday.atTime(1, 0), window.plannedEnd)
        }
    }

    private fun taskOf(
        id: String,
        startMinutes: Int,
        endMinutes: Int,
        repeatRule: RepeatRule = RepeatRule.DAILY,
        customDays: Set<DayOfWeek> = emptySet(),
        isLocked: Boolean = false,
        order: Int = 0,
        createdEpochDay: Long = 0L,
    ): Task = Task(
        id = id,
        planId = "plan-1",
        title = id,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        repeatRule = repeatRule,
        customDays = customDays,
        isLocked = isLocked,
        createdEpochDay = createdEpochDay,
        orderInPlan = order,
    )
}
