package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.model.TimeSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * TaskCompletionInstantiator 单测 — Phase 2.1
 *
 * 覆盖：
 * - 段内任务按时长排程
 * - 多段聚合
 * - 跨午夜段
 * - 段在 date 不生效时不展开
 * - 段内无任务不生成 PENDING
 * - 任务总时长超出段边界
 * - idGenerator 注入（确定性 id）
 */
@DisplayName("TaskCompletionInstantiator")
class TaskCompletionInstantiatorTest {

    private val instantiator = TaskCompletionInstantiator(TimeWindowCalculator())
    private val plan = Plan(
        id = "plan-1",
        title = "晨练计划",
        colorHex = "#C48520",
        startDate = LocalDate.of(2026, 8, 1),
        isActive = true,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )
    private val monday = LocalDate.of(2026, 8, 10) // 周一
    private val saturday = LocalDate.of(2026, 8, 15) // 周六
    private val idGen = sequence { var i = 0; while (true) yield("id-${i++}") }.iterator()::next

    @Nested
    @DisplayName("基础段内排程")
    inner class BasicLayout {
        @Test
        fun `单段单任务 — 任务时段等于段时段`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "晨练",
                startMinutes = 6 * 60,
                endMinutes = 7 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            val task = taskOf("task-1", "seg-1", 60, 0)

            val completions = instantiator(plan, listOf(segment), listOf(task), monday, idGen)
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
        fun `单段多任务按时长累加 — 段内顺序铺`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "晨练",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            val tasks = listOf(
                taskOf("task-A", "seg-1", 20, 0),  // 9:00 - 9:20
                taskOf("task-B", "seg-1", 30, 1),  // 9:20 - 9:50
                taskOf("task-C", "seg-1", 5, 2),   // 9:50 - 9:55
            )

            val completions = instantiator(plan, listOf(segment), tasks, monday, idGen)
            assertEquals(3, completions.size)
            assertEquals(listOf("id-0", "id-1", "id-2"), completions.map { it.id })
            assertEquals(listOf("task-A", "task-B", "task-C"), completions.map { it.taskId })
            assertEquals(monday.atTime(9, 0), completions[0].plannedStart)
            assertEquals(monday.atTime(9, 20), completions[0].plannedEnd)
            assertEquals(monday.atTime(9, 20), completions[1].plannedStart)
            assertEquals(monday.atTime(9, 50), completions[1].plannedEnd)
            assertEquals(monday.atTime(9, 50), completions[2].plannedStart)
            assertEquals(monday.atTime(9, 55), completions[2].plannedEnd)
        }

        @Test
        fun `任务按 orderInSegment 排序而非列表顺序`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "段",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            // 故意打乱列表顺序传入，orderInSegment 应主导排程
            val tasks = listOf(
                taskOf("task-C", "seg-1", 10, 2),
                taskOf("task-A", "seg-1", 10, 0),
                taskOf("task-B", "seg-1", 10, 1),
            )
            val completions = instantiator(plan, listOf(segment), tasks, monday, idGen)
            assertEquals(listOf("task-A", "task-B", "task-C"), completions.map { it.taskId })
            assertEquals(
                listOf(
                    monday.atTime(9, 0),
                    monday.atTime(9, 10),
                    monday.atTime(9, 20),
                ),
                completions.map { it.plannedStart },
            )
        }

        @Test
        fun `段内无任务 — 不生成 PENDING 记录`() {
            val segment = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "空段",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            val completions = instantiator(plan, listOf(segment), emptyList(), monday, idGen)
            assertTrue(completions.isEmpty())
        }
    }

    @Nested
    @DisplayName("多段聚合")
    inner class MultiSegment {
        @Test
        fun `两段都生效时合并返回 各自段的任务 PENDING`() {
            val segMorning = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "晨练",
                startMinutes = 6 * 60,
                endMinutes = 7 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            val segEvening = TimeSegment(
                id = "seg-2",
                planId = plan.id,
                title = "阅读",
                startMinutes = 21 * 60,
                endMinutes = 22 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            val tasks = listOf(
                taskOf("task-1", "seg-1", 30, 0),
                taskOf("task-2", "seg-1", 30, 1),
                taskOf("task-3", "seg-2", 60, 0),
            )

            val completions = instantiator(
                plan,
                listOf(segMorning, segEvening),
                tasks,
                monday,
                idGen,
            )
            assertEquals(3, completions.size)
            val byTask = completions.associateBy { it.taskId }
            // 晨练段
            assertEquals(monday.atTime(6, 0), byTask["task-1"]!!.plannedStart)
            assertEquals(monday.atTime(6, 30), byTask["task-1"]!!.plannedEnd)
            assertEquals(monday.atTime(6, 30), byTask["task-2"]!!.plannedStart)
            assertEquals(monday.atTime(7, 0), byTask["task-2"]!!.plannedEnd)
            // 阅读段
            assertEquals(monday.atTime(21, 0), byTask["task-3"]!!.plannedStart)
            assertEquals(monday.atTime(22, 0), byTask["task-3"]!!.plannedEnd)
        }

        @Test
        fun `过滤掉其他段的任务 — 只展开本 plan 的段所属任务`() {
            val seg = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "本段",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            // task-other 属于不存在的段，应被过滤
            val tasks = listOf(
                taskOf("task-self", "seg-1", 30, 0),
                taskOf("task-other", "seg-X", 30, 0),
            )
            val completions = instantiator(plan, listOf(seg), tasks, monday, idGen)
            assertEquals(1, completions.size)
            assertEquals("task-self", completions.single().taskId)
        }
    }

    @Nested
    @DisplayName("RepeatRule 过滤")
    inner class RepeatRuleFilter {
        @Test
        fun `WEEKDAYS 段在周六不展开`() {
            val seg = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "工作日",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.WEEKDAYS,
            )
            val completions = instantiator(plan, listOf(seg), listOf(taskOf("t1", "seg-1", 60, 0)), saturday, idGen)
            assertTrue(completions.isEmpty())
        }

        @Test
        fun `CUSTOM 段只展开 customDays 内的日期`() {
            val seg = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "周一三五",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.CUSTOM,
                customDays = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY),
            )
            val task = taskOf("t1", "seg-1", 60, 0)
            // 周一展开
            assertEquals(1, instantiator(plan, listOf(seg), listOf(task), monday, idGen).size)
            // 周六不展开
            assertTrue(instantiator(plan, listOf(seg), listOf(task), saturday, idGen).isEmpty())
        }
    }

    @Nested
    @DisplayName("跨午夜段")
    inner class CrossMidnight {
        @Test
        fun `跨午夜段任务时段延续到次日`() {
            val seg = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "夜班",
                startMinutes = 23 * 60,  // 23:00
                endMinutes = 25 * 60,    // 次日 01:00
                repeatRule = RepeatRule.DAILY,
            )
            val task = taskOf("t-night", "seg-1", 120, 0)
            val completions = instantiator(plan, listOf(seg), listOf(task), monday, idGen)
            assertEquals(1, completions.size)
            val c = completions.single()
            assertEquals(monday.atTime(23, 0), c.plannedStart)
            assertEquals(monday.plusDays(1).atTime(1, 0), c.plannedEnd)
        }
    }

    @Nested
    @DisplayName("溢出")
    inner class Overflow {
        @Test
        fun `任务总时长超出段边界 — 最后一个任务的 plannedEnd 延到段外`() {
            val seg = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "短段",
                startMinutes = 9 * 60,   // 9:00
                endMinutes = 10 * 60,    // 10:00 (60 min)
                repeatRule = RepeatRule.DAILY,
            )
            val tasks = listOf(
                taskOf("t-A", "seg-1", 30, 0),  // 9:00-9:30
                taskOf("t-B", "seg-1", 50, 1),  // 9:30-10:20 超出段边界
            )
            val completions = instantiator(plan, listOf(seg), tasks, monday, idGen)
            assertEquals(2, completions.size)
            assertEquals(monday.atTime(9, 0), completions[0].plannedStart)
            assertEquals(monday.atTime(9, 30), completions[0].plannedEnd)
            assertEquals(monday.atTime(9, 30), completions[1].plannedStart)
            assertEquals(monday.atTime(10, 20), completions[1].plannedEnd) // 段外 OK
        }
    }

    @Nested
    @DisplayName("PENDING 字段")
    inner class PendingDefaults {
        @Test
        fun `生成的 completion 默认 PENDING + overrun=0 + affectedTasks=空`() {
            val seg = TimeSegment(
                id = "seg-1",
                planId = plan.id,
                title = "段",
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
                repeatRule = RepeatRule.DAILY,
            )
            val completions = instantiator(plan, listOf(seg), listOf(taskOf("t1", "seg-1", 30, 0)), monday, idGen)
            val c = completions.single()
            assertEquals(TaskStatus.PENDING, c.status)
            assertEquals(0, c.overrunMinutes)
            assertTrue(c.affectedTasks.isEmpty())
            assertEquals(null, c.actualStart)  // 默认 null（未开始）
            assertEquals(null, c.actualEnd)
        }
    }

    // ---- helpers ----

    private fun taskOf(
        id: String,
        segmentId: String,
        durationMinutes: Int,
        order: Int,
    ): Task = Task(
        id = id,
        segmentId = segmentId,
        title = id,
        plannedDurationMinutes = durationMinutes,
        autoExtend = true,
        isLocked = false,
        orderInSegment = order,
    )
}
