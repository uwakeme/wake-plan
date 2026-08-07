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
 * AutoExtendCalculator 单测 — Phase 2.2
 *
 * 覆盖：
 * - 基础延期：IN_PROGRESS overrun 推下游 PENDING
 * - 不超时 / COMPLETED / SKIPPED 不延期
 * - isLocked / autoExtend=false 跳过顺延
 * - Cascade：多个 IN_PROGRESS 累加
 * - affectedTasks 正确累加
 * - 跨午夜段
 * - 锁死后 currentOffset 仍传递给后续 PENDING
 */
@DisplayName("AutoExtendCalculator")
class AutoExtendCalculatorTest {

    private val calculator = AutoExtendCalculator()
    private val date = LocalDate.of(2026, 8, 10)
    private val planId = "plan-1"

    // ---- helpers ----

    private fun completion(
        id: String,
        taskId: String,
        start: LocalDateTime,
        end: LocalDateTime,
        status: TaskStatus = TaskStatus.PENDING,
    ): TaskCompletion = TaskCompletion(
        id = id,
        taskId = taskId,
        planId = planId,
        scheduledDate = date,
        plannedStart = start,
        plannedEnd = end,
        status = status,
    )

    private fun task(
        id: String,
        autoExtend: Boolean = true,
        isLocked: Boolean = false,
    ): Task = Task(
        id = id,
        segmentId = "seg-1",
        title = id,
        plannedDurationMinutes = 30,
        autoExtend = autoExtend,
        isLocked = isLocked,
        orderInSegment = 0,
    )

    @Nested
    @DisplayName("基础延期")
    inner class Basic {
        @Test
        fun `IN_PROGRESS overrun 5min 时 PENDING 推 5min`() {
            val a = completion(
                "c-A", "t-A",
                date.atTime(9, 0), date.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
            )
            val b = completion(
                "c-B", "t-B",
                date.atTime(9, 30), date.atTime(10, 0),
                status = TaskStatus.PENDING,
            )
            val now = date.atTime(9, 35)  // A 已超 5min

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), now)
            assertEquals(2, result.size)

            val outA = result.single { it.id == "c-A" }
            val outB = result.single { it.id == "c-B" }
            assertEquals(5, outA.overrunMinutes)  // A 的 overrun 被记录
            assertEquals(date.atTime(9, 35), outB.plannedStart)  // 9:30 + 5min
            assertEquals(date.atTime(10, 5), outB.plannedEnd)
            assertEquals(listOf("t-A"), outB.affectedTasks)
        }

        @Test
        fun `IN_PROGRESS 还没超时 (overrun=0) 不延期下游`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.IN_PROGRESS)
            val b = completion("c-B", "t-B", date.atTime(9, 30), date.atTime(10, 0))
            val now = date.atTime(9, 20)  // 在 A 计划时段内

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), now)
            val outA = result.single { it.id == "c-A" }
            val outB = result.single { it.id == "c-B" }
            assertEquals(0, outA.overrunMinutes)
            assertEquals(date.atTime(9, 30), outB.plannedStart)  // 不动
            assertEquals(date.atTime(10, 0), outB.plannedEnd)
            assertTrue(outB.affectedTasks.isEmpty())
        }

        @Test
        fun `IN_PROGRESS 已完结超时 (now 超 plannedEnd) 时按实际超时算`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.IN_PROGRESS)
            val b = completion("c-B", "t-B", date.atTime(9, 30), date.atTime(10, 0))
            val now = date.atTime(9, 50)  // 超 20min

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), now)
            val outB = result.single { it.id == "c-B" }
            assertEquals(date.atTime(9, 50), outB.plannedStart)  // 9:30 + 20
            assertEquals(listOf("t-A"), outB.affectedTasks)
        }
    }

    @Nested
    @DisplayName("COMPLETED / SKIPPED 不延期")
    inner class TerminalStatus {
        @Test
        fun `COMPLETED 不延期下游`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.COMPLETED)
            val b = completion("c-B", "t-B", date.atTime(9, 30), date.atTime(10, 0))
            val now = date.atTime(9, 50)  // A 实际"超时"了但状态 COMPLETED

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), now)
            val outA = result.single { it.id == "c-A" }
            val outB = result.single { it.id == "c-B" }
            // COMPLETED 的 overrunMinutes 也不更新（保持 0，COMPLETED 自带完成时间）
            assertEquals(0, outA.overrunMinutes)
            assertEquals(date.atTime(9, 30), outB.plannedStart)
            assertTrue(outB.affectedTasks.isEmpty())
        }

        @Test
        fun `SKIPPED 不延期下游`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.SKIPPED)
            val b = completion("c-B", "t-B", date.atTime(9, 30), date.atTime(10, 0))
            val now = date.atTime(9, 50)

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), now)
            val outB = result.single { it.id == "c-B" }
            assertEquals(date.atTime(9, 30), outB.plannedStart)
        }
    }

    @Nested
    @DisplayName("isLocked / autoExtend 守卫")
    inner class Guards {
        @Test
        fun `PENDING 对应 task isLocked=true 不动`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.IN_PROGRESS)
            val bLocked = completion("c-B", "t-B-locked", date.atTime(9, 30), date.atTime(10, 0))
            val c = completion("c-C", "t-C", date.atTime(10, 0), date.atTime(10, 30))
            val now = date.atTime(9, 35)

            val result = calculator(
                listOf(a, bLocked, c),
                listOf(
                    task("t-A"),
                    task("t-B-locked", isLocked = true),  // B 锁死
                    task("t-C"),
                ),
                now,
            )
            // B 不动；currentOffset 仍传递给 C
            assertEquals(date.atTime(9, 30), result.single { it.id == "c-B" }.plannedStart)
            assertEquals(date.atTime(10, 5), result.single { it.id == "c-C" }.plannedStart)
            assertEquals(listOf("t-A"), result.single { it.id == "c-C" }.affectedTasks)
        }

        @Test
        fun `PENDING 对应 task autoExtend=false 不动`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.IN_PROGRESS)
            val bNoExtend = completion("c-B", "t-B-nex", date.atTime(9, 30), date.atTime(10, 0))
            val now = date.atTime(9, 35)

            val result = calculator(
                listOf(a, bNoExtend),
                listOf(task("t-A"), task("t-B-nex", autoExtend = false)),
                now,
            )
            assertEquals(date.atTime(9, 30), result.single { it.id == "c-B" }.plannedStart)
        }

        @Test
        fun `PENDING 对应 task 在 tasks 里找不到时不延期`() {
            // 防御性：completion 引用了不存在的 task
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.IN_PROGRESS)
            val bOrphan = completion("c-B", "t-orphan", date.atTime(9, 30), date.atTime(10, 0))
            val now = date.atTime(9, 35)

            val result = calculator(listOf(a, bOrphan), listOf(task("t-A")), now)
            assertEquals(date.atTime(9, 30), result.single { it.id == "c-B" }.plannedStart)
        }
    }

    @Nested
    @DisplayName("Cascade")
    inner class Cascade {
        @Test
        fun `两个 IN_PROGRESS 累加 — 下游 PENDING 推两者之和`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.IN_PROGRESS)
            val b = completion("c-B", "t-B", date.atTime(9, 30), date.atTime(10, 0), status = TaskStatus.IN_PROGRESS)
            val c = completion("c-C", "t-C", date.atTime(10, 0), date.atTime(10, 30))
            // now=10:03: A overrun=33min (10:03-9:30), B overrun=3min (10:03-10:00) → 累加 36min
            val now = date.atTime(10, 3)

            val result = calculator(listOf(a, b, c), listOf(task("t-A"), task("t-B"), task("t-C")), now)
            val outC = result.single { it.id == "c-C" }
            assertEquals(date.atTime(10, 36), outC.plannedStart)  // 10:00 + 36min
            assertEquals(date.atTime(11, 6), outC.plannedEnd)
            assertEquals(listOf("t-A", "t-B"), outC.affectedTasks)
        }

        @Test
        fun `IN_PROGRESS 自身的 plannedStart_End 不被改写`() {
            // 关键：延期只动下游 PENDING，不动 IN_PROGRESS 自己
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.IN_PROGRESS)
            val now = date.atTime(9, 50)
            val result = calculator(listOf(a), listOf(task("t-A")), now)
            val outA = result.single()
            assertEquals(date.atTime(9, 0), outA.plannedStart)
            assertEquals(date.atTime(9, 30), outA.plannedEnd)
            assertEquals(20, outA.overrunMinutes)  // 只更新 overrunMinutes
        }
    }

    @Nested
    @DisplayName("跨午夜")
    inner class CrossMidnight {
        @Test
        fun `延期分钟数跨过午夜 — plannedStart 自动跨日`() {
            // 23:00 开始的段，任务 A 23:00-23:30 overrun 90min → 现在次日 00:30
            val a = completion(
                "c-A", "t-A",
                date.atTime(23, 0), date.atTime(23, 30),
                status = TaskStatus.IN_PROGRESS,
            )
            val b = completion(
                "c-B", "t-B",
                date.atTime(23, 30), date.plusDays(1).atTime(0, 0),  // 23:30-24:00
            )
            val now = date.plusDays(1).atTime(1, 0)  // 超 90min

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), now)
            val outA = result.single { it.id == "c-A" }
            val outB = result.single { it.id == "c-B" }
            assertEquals(90, outA.overrunMinutes)
            // B 从 23:30 + 90min = 次日 01:00
            assertEquals(date.plusDays(1).atTime(1, 0), outB.plannedStart)
            assertEquals(date.plusDays(1).atTime(1, 30), outB.plannedEnd)
            assertEquals(listOf("t-A"), outB.affectedTasks)
        }
    }

    @Nested
    @DisplayName("边界")
    inner class Edge {
        @Test
        fun `空 completions 列表 — 返回空列表`() {
            val result = calculator(emptyList(), emptyList(), date.atTime(9, 0))
            assertTrue(result.isEmpty())
        }

        @Test
        fun `无 IN_PROGRESS — 全 PENDING 不动`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30))
            val b = completion("c-B", "t-B", date.atTime(9, 30), date.atTime(10, 0))
            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), date.atTime(11, 0))
            assertEquals(date.atTime(9, 0), result[0].plannedStart)
            assertEquals(date.atTime(9, 30), result[1].plannedStart)
            assertTrue(result[1].affectedTasks.isEmpty())
        }

        @Test
        fun `输入列表不被修改（不变量）`() {
            val a = completion("c-A", "t-A", date.atTime(9, 0), date.atTime(9, 30), status = TaskStatus.IN_PROGRESS)
            val b = completion("c-B", "t-B", date.atTime(9, 30), date.atTime(10, 0))
            val originalA = a.copy()
            val originalB = b.copy()
            calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), date.atTime(9, 50))
            assertEquals(originalA, a)
            assertEquals(originalB, b)
        }
    }
}
