package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ScheduleManager 单测 — Phase 3.2
 *
 * 纯 UseCase，pure JVM 测试
 * 覆盖：PENDING 过滤 / triggerAt = plannedStart - lead / 跨日跳过 / startingEnabled 关掉
 */
@DisplayName("ScheduleManager")
class ScheduleManagerTest {

    private val scheduleManager = ScheduleManager()
    private val date = LocalDate.of(2026, 8, 10)
    private val now = date.atTime(16, 0)  // 下午 4 点

    private fun completion(
        id: String,
        taskId: String,
        start: LocalDateTime,
        end: LocalDateTime,
        status: TaskStatus = TaskStatus.PENDING,
    ): TaskCompletion = TaskCompletion(
        id = id, taskId = taskId, planId = "p-1",
        scheduledDate = date, plannedStart = start, plannedEnd = end, status = status,
    )

    private fun task(id: String) = Task(
        id = id, segmentId = "s-1", title = "t-$id",
        plannedDurationMinutes = 30, autoExtend = true, isLocked = false, orderInSegment = 0,
    )

    @Test
    fun `基础 — PENDING 任务 triggerAt = plannedStart - leadMinutes`() {
        val c = completion("c-1", "t-1", date.atTime(17, 0), date.atTime(17, 30))
        val plan = scheduleManager(
            completions = listOf(c),
            tasks = listOf(task("t-1")),
            now = now,
            leadMinutes = 5,
        )
        assertEquals(1, plan.startingTriggers.size)
        val trigger = plan.startingTriggers.single()
        assertEquals("c-1", trigger.completionId)
        assertEquals(date.atTime(16, 55), trigger.triggerAt)
    }

    @Test
    fun `lead=0 时 triggerAt = plannedStart (立即触发)`() {
        val c = completion("c-1", "t-1", date.atTime(17, 0), date.atTime(17, 30))
        val plan = scheduleManager(listOf(c), listOf(task("t-1")), now, leadMinutes = 0)
        assertEquals(date.atTime(17, 0), plan.startingTriggers.single().triggerAt)
    }

    @Test
    fun `IN_PROGRESS 的任务不调度 starting alarm`() {
        val c = completion("c-1", "t-1", date.atTime(17, 0), date.atTime(17, 30), status = TaskStatus.IN_PROGRESS)
        val plan = scheduleManager(listOf(c), listOf(task("t-1")), now, 5)
        assertTrue(plan.startingTriggers.isEmpty())
    }

    @Test
    fun `COMPLETED 的任务不调度`() {
        val c = completion("c-1", "t-1", date.atTime(17, 0), date.atTime(17, 30), status = TaskStatus.COMPLETED)
        val plan = scheduleManager(listOf(c), listOf(task("t-1")), now, 5)
        assertTrue(plan.startingTriggers.isEmpty())
    }

    @Test
    fun `SKIPPED 的任务不调度`() {
        val c = completion("c-1", "t-1", date.atTime(17, 0), date.atTime(17, 30), status = TaskStatus.SKIPPED)
        val plan = scheduleManager(listOf(c), listOf(task("t-1")), now, 5)
        assertTrue(plan.startingTriggers.isEmpty())
    }

    @Test
    fun `task 引用找不到时跳过（防御性）`() {
        val c = completion("c-1", "t-orphan", date.atTime(17, 0), date.atTime(17, 30))
        val plan = scheduleManager(listOf(c), listOf(task("t-other")), now, 5)
        assertTrue(plan.startingTriggers.isEmpty())
    }

    @Test
    fun `triggerAt 超过 now+24h 跳过（明天再 schedule）`() {
        // now = 16:00；任务在 明天 20:00 (28h 后) → triggerAt = 明天 19:55 > now+24h
        val c = completion("c-1", "t-1", date.plusDays(1).atTime(20, 0), date.plusDays(1).atTime(20, 30))
        val plan = scheduleManager(listOf(c), listOf(task("t-1")), now, 5)
        assertTrue(plan.startingTriggers.isEmpty())
    }

    @Test
    fun `triggerAt 已在过去仍 schedule（AlarmManager 立即触发）`() {
        val c = completion("c-1", "t-1", date.atTime(15, 0), date.atTime(15, 30))  // 已过
        val plan = scheduleManager(listOf(c), listOf(task("t-1")), now, 5)
        // triggerAt = 14:55，已过去 — 但仍 schedule（AlarmManager 立即 fire）
        assertEquals(1, plan.startingTriggers.size)
        assertEquals(date.atTime(14, 55), plan.startingTriggers.single().triggerAt)
    }

    @Test
    fun `startingEnabled=false 返回空 plan`() {
        val c = completion("c-1", "t-1", date.atTime(17, 0), date.atTime(17, 30))
        val plan = scheduleManager(
            listOf(c), listOf(task("t-1")), now, 5,
            startingEnabled = false,
        )
        assertTrue(plan.startingTriggers.isEmpty())
    }

    @Test
    fun `多 completion 按 triggerAt 升序排`() {
        val a = completion("c-A", "t-A", date.atTime(20, 0), date.atTime(20, 30))  // trigger 19:55
        val b = completion("c-B", "t-B", date.atTime(18, 0), date.atTime(18, 30))  // trigger 17:55
        val c = completion("c-C", "t-C", date.atTime(19, 0), date.atTime(19, 30))  // trigger 18:55
        val plan = scheduleManager(
            listOf(a, b, c), listOf(task("t-A"), task("t-B"), task("t-C")),
            now, leadMinutes = 5,
        )
        assertEquals(listOf("c-B", "c-C", "c-A"), plan.startingTriggers.map { it.completionId })
    }

    @Test
    fun `空列表返回空 plan`() {
        val plan = scheduleManager(emptyList(), emptyList(), now, 5)
        assertTrue(plan.startingTriggers.isEmpty())
    }
}
