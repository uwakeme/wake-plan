package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@DisplayName("TaskCompletionInstantiator")
class TaskCompletionInstantiatorTest {

    private val instantiator = TaskCompletionInstantiator(TaskWindowCalculator())
    private val plan = Plan(
        id = "plan-1",
        title = "晨练计划",
        colorHex = "#C48520",
        startDate = LocalDate.of(2026, 8, 1),
        isActive = true,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )
    private val monday = LocalDate.of(2026, 8, 10)
    private val saturday = LocalDate.of(2026, 8, 15)
    private val idGen = sequence { var i = 0; while (true) yield("id-${i++}") }.iterator()::next

    @Nested
    @DisplayName("基础排程")
    inner class Basic {
        @Test
        fun `单 task — 任务时段等于 task 自身窗口`() {
            val task = taskOf(id = "task-1", startMinutes = 6 * 60, endMinutes = 7 * 60)
            val completions = instantiator(plan, listOf(task), monday, idGen)
            assertEquals(1, completions.size)
            val c = completions.single()
            assertEquals("id-0", c.id)
            assertEquals(plan.id, c.planId)
            assertEquals("task-1", c.taskId)
            assertEquals(monday, c.scheduledDate)
            assertEquals(monday.atTime(6, 0), c.plannedStart)
            assertEquals(monday.atTime(7, 0), c.plannedEnd)
            assertEquals(TaskStatus.PENDING, c.status)
        }

        @Test
        fun `多 task — 每个 task 独立生成 completion（不累加）`() {
            val tasks = listOf(
                taskOf(id = "task-A", startMinutes = 9 * 60, endMinutes = 9 * 60 + 30),
                taskOf(id = "task-B", startMinutes = 10 * 60, endMinutes = 10 * 60 + 30),
                taskOf(id = "task-C", startMinutes = 14 * 60, endMinutes = 14 * 60 + 15),
            )
            val completions = instantiator(plan, tasks, monday, idGen)
            assertEquals(3, completions.size)
            assertEquals(listOf("id-0", "id-1", "id-2"), completions.map { it.id })
            assertEquals(listOf("task-A", "task-B", "task-C"), completions.map { it.taskId })
            assertEquals(monday.atTime(9, 0), completions[0].plannedStart)
            assertEquals(monday.atTime(9, 30), completions[0].plannedEnd)
            assertEquals(monday.atTime(10, 0), completions[1].plannedStart)
            assertEquals(monday.atTime(10, 30), completions[1].plannedEnd)
            assertEquals(monday.atTime(14, 0), completions[2].plannedStart)
            assertEquals(monday.atTime(14, 15), completions[2].plannedEnd)
        }

        @Test
        fun `空 task 列表 — 返回空 list`() {
            val completions = instantiator(plan, emptyList(), monday, idGen)
            assertTrue(completions.isEmpty())
        }
    }

    @Nested
    @DisplayName("RepeatRule 过滤")
    inner class RepeatRuleFilter {
        @Test
        fun `WEEKDAYS task 在周六不展开`() {
            val task = taskOf(id = "t1", startMinutes = 9 * 60, endMinutes = 10 * 60, repeatRule = RepeatRule.WEEKDAYS)
            val completions = instantiator(plan, listOf(task), saturday, idGen)
            assertTrue(completions.isEmpty())
        }
    }

    @Nested
    @DisplayName("PENDING 字段")
    inner class PendingDefaults {
        @Test
        fun `PENDING 字段`() {
            val task = taskOf(id = "t1", startMinutes = 9 * 60, endMinutes = 10 * 60)
            val completions = instantiator(plan, listOf(task), monday, idGen)
            val c = completions.single()
            assertEquals(TaskStatus.PENDING, c.status)
            assertEquals(0, c.overrunMinutes)
            assertTrue(c.affectedTasks.isEmpty())
        }
    }

    private fun taskOf(
        id: String,
        startMinutes: Int,
        endMinutes: Int,
        repeatRule: RepeatRule = RepeatRule.DAILY,
        customDays: Set<java.time.DayOfWeek> = emptySet(),
        isLocked: Boolean = false,
        order: Int = 0,
    ): Task = Task(
        id = id,
        planId = plan.id,
        title = id,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        repeatRule = repeatRule,
        customDays = customDays,
        isLocked = isLocked,
        orderInPlan = order,
    )
}
