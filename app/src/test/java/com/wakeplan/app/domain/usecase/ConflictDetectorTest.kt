package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ConflictDetector 单测 — Phase 2.3
 *
 * 覆盖：
 * - 半开区间 [start, end)：a.end == b.start 不算重叠
 * - 多对冲突只返一次（无序对）
 * - SKIPPED 排除
 * - COMPLETED / IN_PROGRESS / PENDING 都参与
 * - 跨 plan
 * - 跨午夜段
 * - 不同日期过滤
 * - overlapMinutes 计算
 */
@DisplayName("ConflictDetector")
class ConflictDetectorTest {

    private val detector = ConflictDetector()
    private val date = LocalDate.of(2026, 8, 10)
    private val otherDate = LocalDate.of(2026, 8, 11)

    // ---- helpers ----

    private fun completion(
        id: String,
        taskId: String,
        planId: String = "plan-1",
        start: LocalDateTime,
        end: LocalDateTime,
        status: TaskStatus = TaskStatus.PENDING,
        scheduledDate: LocalDate = date,
    ): TaskCompletion = TaskCompletion(
        id = id,
        taskId = taskId,
        planId = planId,
        scheduledDate = scheduledDate,
        plannedStart = start,
        plannedEnd = end,
        status = status,
    )

    @Nested
    @DisplayName("半开区间")
    inner class HalfOpen {
        @Test
        fun `a_end == b_start 不算重叠`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            val b = completion("c-B", "t-B", start = date.atTime(10, 0), end = date.atTime(11, 0))
            val conflicts = detector(listOf(a, b), date)
            assertTrue(conflicts.isEmpty())
        }

        @Test
        fun `a 包含 b — 整段重叠`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(11, 0))
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val conflicts = detector(listOf(a, b), date)
            assertEquals(1, conflicts.size)
            val c = conflicts.single()
            assertEquals(date.atTime(9, 30), c.overlapStart)
            assertEquals(date.atTime(10, 30), c.overlapEnd)
            assertEquals(60, c.overlapMinutes)
        }

        @Test
        fun `部分重叠 — 取交集`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val conflicts = detector(listOf(a, b), date)
            assertEquals(1, conflicts.size)
            val c = conflicts.single()
            assertEquals(date.atTime(9, 30), c.overlapStart)
            assertEquals(date.atTime(10, 0), c.overlapEnd)
            assertEquals(30, c.overlapMinutes)
        }

        @Test
        fun `完全分离 — 无冲突`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            val b = completion("c-B", "t-B", start = date.atTime(11, 0), end = date.atTime(12, 0))
            assertTrue(detector(listOf(a, b), date).isEmpty())
        }
    }

    @Nested
    @DisplayName("多对")
    inner class MultiPair {
        @Test
        fun `三个任务两两冲突 — 返回 3 对`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val c = completion("c-C", "t-C", start = date.atTime(9, 45), end = date.atTime(10, 15))
            val conflicts = detector(listOf(a, b, c), date)
            assertEquals(3, conflicts.size)  // A-B, A-C, B-C
            val pairs = conflicts.map { setOf(it.taskA.id, it.taskB.id) }.toSet()
            assertTrue(setOf("c-A", "c-B") in pairs)
            assertTrue(setOf("c-A", "c-C") in pairs)
            assertTrue(setOf("c-B", "c-C") in pairs)
        }

        @Test
        fun `三个任务只有 A-B 撞 — 只返回 1 对`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val c = completion("c-C", "t-C", start = date.atTime(11, 0), end = date.atTime(12, 0))
            val conflicts = detector(listOf(a, b, c), date)
            assertEquals(1, conflicts.size)
            val pair = setOf(conflicts.single().taskA.id, conflicts.single().taskB.id)
            assertEquals(setOf("c-A", "c-B"), pair)
        }

        @Test
        fun `无序对 a-b 和 b-a 视为同一对 — 只返一次`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val conflicts = detector(listOf(b, a), date)  // 故意 b 在前
            assertEquals(1, conflicts.size)
        }
    }

    @Nested
    @DisplayName("状态过滤")
    inner class StatusFilter {
        @Test
        fun `SKIPPED 排除 — 不参与冲突检查`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0), status = TaskStatus.SKIPPED)
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            assertTrue(detector(listOf(a, b), date).isEmpty())
        }

        @Test
        fun `COMPLETED 参与 — 撞了也要警告用户`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0), status = TaskStatus.COMPLETED)
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val conflicts = detector(listOf(a, b), date)
            assertEquals(1, conflicts.size)
        }

        @Test
        fun `IN_PROGRESS 参与`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0), status = TaskStatus.IN_PROGRESS)
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val conflicts = detector(listOf(a, b), date)
            assertEquals(1, conflicts.size)
        }
    }

    @Nested
    @DisplayName("跨 plan / 跨日")
    inner class CrossPlanAndDate {
        @Test
        fun `跨 plan 任务撞了也算冲突`() {
            val a = completion("c-A", "t-A", planId = "plan-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            val b = completion("c-B", "t-B", planId = "plan-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val conflicts = detector(listOf(a, b), date)
            assertEquals(1, conflicts.size)
            // a 和 b planId 不同
            assertNotEquals(conflicts.single().taskA.planId, conflicts.single().taskB.planId)
        }

        @Test
        fun `不同日期 — 不参与冲突检查`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0), scheduledDate = date)
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30), scheduledDate = otherDate)
            val conflicts = detector(listOf(a, b), date)
            assertTrue(conflicts.isEmpty())
        }
    }

    @Nested
    @DisplayName("跨午夜")
    inner class CrossMidnight {
        @Test
        fun `跨午夜段的任务冲突检测正常`() {
            // 任务 A 23:00-23:30 (跨午夜段里)
            // 任务 B 23:15-次日 00:15 (跨午夜段里)
            // 冲突 23:15-23:30 = 15min
            val a = completion("c-A", "t-A", start = date.atTime(23, 0), end = date.atTime(23, 30))
            val b = completion(
                "c-B", "t-B",
                start = date.atTime(23, 15),
                end = date.plusDays(1).atTime(0, 15),
            )
            val conflicts = detector(listOf(a, b), date)
            assertEquals(1, conflicts.size)
            val c = conflicts.single()
            assertEquals(date.atTime(23, 15), c.overlapStart)
            assertEquals(date.atTime(23, 30), c.overlapEnd)
            assertEquals(15, c.overlapMinutes)
        }
    }

    @Nested
    @DisplayName("边界")
    inner class Edge {
        @Test
        fun `空 completions — 返回空`() {
            assertTrue(detector(emptyList(), date).isEmpty())
        }

        @Test
        fun `单 completion — 返回空（需要 2 个才能冲突）`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            assertTrue(detector(listOf(a), date).isEmpty())
        }

        @Test
        fun `输入列表不被修改`() {
            val a = completion("c-A", "t-A", start = date.atTime(9, 0), end = date.atTime(10, 0))
            val b = completion("c-B", "t-B", start = date.atTime(9, 30), end = date.atTime(10, 30))
            val originalA = a.copy()
            val originalB = b.copy()
            detector(listOf(a, b), date)
            assertEquals(originalA, a)
            assertEquals(originalB, b)
        }
    }
}
