package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.RepeatRule
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
 * CascadeDelayCalculator 单测 — v0.3 链式碰撞延期
 *
 * 覆盖（设计稿 §3.4）：
 * 1. 单个超时推下一个
 * 2. 链式 3 个（A 超时 → 推 B → B 也超时 → 推 C）
 * 3. locked 钉死，链断
 * 4. 已完成的不影响链
 * 5. 跨 plan 任务混排
 * 6. 不影响明天（scheduledDate != today 不参与）
 *
 * 关键模型：
 * - prev IN_PROGRESS 时 actualEnd = now（或已落库的实际结束时间）
 * - 算法用 computedEndAt = actualEnd ?: plannedEnd
 * - prev.status = COMPLETED/SKIPPED → 不推下游（即使 actualEnd 超过 cur 计划）
 */
@DisplayName("CascadeDelayCalculator")
class CascadeDelayCalculatorTest {

    private val calculator = CascadeDelayCalculator()
    private val today = LocalDate.of(2026, 8, 10)
    private val tomorrow = today.plusDays(1)
    private val now = today.atTime(9, 45)
    private val planA = "plan-A"
    private val planB = "plan-B"

    // ---- helpers ----

    private fun completion(
        id: String,
        taskId: String,
        planId: String = planA,
        start: LocalDateTime,
        end: LocalDateTime,
        status: TaskStatus = TaskStatus.PENDING,
        actualStart: LocalDateTime? = null,
        actualEnd: LocalDateTime? = null,
        scheduledDate: LocalDate = today,
    ): TaskCompletion = TaskCompletion(
        id = id,
        taskId = taskId,
        planId = planId,
        scheduledDate = scheduledDate,
        plannedStart = start,
        plannedEnd = end,
        actualStart = actualStart,
        actualEnd = actualEnd,
        status = status,
    )

    private fun task(
        id: String,
        isLocked: Boolean = false,
        startMinutes: Int = 0,
        endMinutes: Int = 30,
    ): Task = Task(
        id = id,
        planId = planA,
        title = id,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        repeatRule = RepeatRule.DAILY,
        isLocked = isLocked,
        orderInPlan = 0,
    )

    @Nested
    @DisplayName("基础链式碰撞")
    inner class Basic {
        @Test
        fun `单个超时推下一个 — actualEnd 推 PENDING 后续`() {
            // A 9:00-9:30 IN_PROGRESS actualEnd=9:45
            // B 9:30-10:00 PENDING
            // 期望：B 推到 9:45-10:15，affectedTasks = [A]
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(9, 45),
            )
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), today, now)
            val outA = result.single { it.id == "c-A" }
            val outB = result.single { it.id == "c-B" }

            // A 自身不变（IN_PROGRESS 不被改）
            assertEquals(today.atTime(9, 0), outA.plannedStart)
            assertEquals(today.atTime(9, 30), outA.plannedEnd)
            // B 被推 15min
            assertEquals(today.atTime(9, 45), outB.plannedStart)
            assertEquals(today.atTime(10, 15), outB.plannedEnd)
            assertEquals(listOf("t-A"), outB.affectedTasks)
        }

        @Test
        fun `链式 3 个 — A 推 B，B 推 C，affectedTasks 各自记录上游`() {
            // A 9:00-9:30 IN_PROGRESS actualEnd=10:00
            // B 9:30-10:00 PENDING
            // C 10:00-10:30 PENDING
            // 第一轮：A 推 B → B 变 10:00-10:30
            // 第二轮：B (现在 10:00-10:30) 推 C → C 变 10:30-11:00
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(10, 0),
            )
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))
            val c = completion("c-C", "t-C", start = today.atTime(10, 0), end = today.atTime(10, 30))

            val result = calculator(
                listOf(a, b, c),
                listOf(task("t-A"), task("t-B"), task("t-C")),
                today, now,
            )

            val outA = result.single { it.id == "c-A" }
            val outB = result.single { it.id == "c-B" }
            val outC = result.single { it.id == "c-C" }

            assertEquals(today.atTime(9, 0), outA.plannedStart)
            assertEquals(today.atTime(10, 0), outB.plannedStart)
            assertEquals(today.atTime(10, 30), outB.plannedEnd)
            assertEquals(listOf("t-A"), outB.affectedTasks)
            assertEquals(today.atTime(10, 30), outC.plannedStart)
            assertEquals(today.atTime(11, 0), outC.plannedEnd)
            assertEquals(listOf("t-B"), outC.affectedTasks)
        }
    }

    @Nested
    @DisplayName("locked 钉死")
    inner class Locked {
        @Test
        fun `中间 locked 钉死 — 链断，locked 自己不动，locked 之后也不动`() {
            // A 9:00-9:30 IN_PROGRESS actualEnd=9:45
            // B 9:30-10:00 PENDING isLocked=true
            // C 10:00-10:30 PENDING
            // 期望：A 不变；B 不变（钉死）；C 不变（链断）
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(9, 45),
            )
            val b = completion("c-B", "t-B-locked", start = today.atTime(9, 30), end = today.atTime(10, 0))
            val c = completion("c-C", "t-C", start = today.atTime(10, 0), end = today.atTime(10, 30))

            val result = calculator(
                listOf(a, b, c),
                listOf(task("t-A"), task("t-B-locked", isLocked = true), task("t-C")),
                today, now,
            )

            val outA = result.single { it.id == "c-A" }
            val outB = result.single { it.id == "c-B" }
            val outC = result.single { it.id == "c-C" }
            assertEquals(today.atTime(9, 30), outA.plannedEnd)  // A 不变
            assertEquals(today.atTime(9, 30), outB.plannedStart)  // B 不动
            assertEquals(today.atTime(10, 0), outB.plannedEnd)
            assertTrue(outB.affectedTasks.isEmpty())
            assertEquals(today.atTime(10, 0), outC.plannedStart)  // C 不动（链断）
            assertEquals(today.atTime(10, 30), outC.plannedEnd)
            assertTrue(outC.affectedTasks.isEmpty())
        }

        @Test
        fun `第一个就 locked — 没有任何 task 被推`() {
            // A 9:00-9:30 PENDING isLocked=true
            // B 9:30-10:00 PENDING
            // 期望：A locked 触发 break，B 不被推
            val a = completion("c-A", "t-A-locked", start = today.atTime(9, 0), end = today.atTime(9, 30))
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))

            val result = calculator(
                listOf(a, b),
                listOf(task("t-A-locked", isLocked = true), task("t-B")),
                today, now,
            )
            assertEquals(today.atTime(9, 0), result.single { it.id == "c-A" }.plannedStart)
            assertEquals(today.atTime(9, 30), result.single { it.id == "c-B" }.plannedStart)
        }
    }

    @Nested
    @DisplayName("终态不影响链")
    inner class Terminal {
        @Test
        fun `COMPLETED prev 不推下游 — 即使 actualEnd 超 cur start`() {
            // A 9:00-9:30 COMPLETED actualEnd=9:25
            // B 9:30-10:00 PENDING
            // 期望：B 不动（COMPLETED 不推）
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.COMPLETED,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(9, 25),
            )
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), today, now)
            val outB = result.single { it.id == "c-B" }
            assertEquals(today.atTime(9, 30), outB.plannedStart)
            assertEquals(today.atTime(10, 0), outB.plannedEnd)
            assertTrue(outB.affectedTasks.isEmpty())
        }

        @Test
        fun `SKIPPED prev 不推下游`() {
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.SKIPPED,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(9, 20),
            )
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))

            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), today, now)
            assertEquals(today.atTime(9, 30), result.single { it.id == "c-B" }.plannedStart)
        }

        @Test
        fun `COMPLETED cur 不被推 — 即使 prev IN_PROGRESS overrun`() {
            // A 9:00-9:30 IN_PROGRESS actualEnd=10:00
            // B 9:30-10:00 COMPLETED
            // C 10:00-10:30 PENDING
            // 期望：pair(A,B) prev 推 cur 但 cur 是 COMPLETED → continue
            //      pair(B,C) prev 是 COMPLETED → continue
            //      C 不动
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(10, 0),
            )
            val b = completion(
                "c-B", "t-B",
                start = today.atTime(9, 30), end = today.atTime(10, 0),
                status = TaskStatus.COMPLETED,
                actualStart = today.atTime(9, 30),
                actualEnd = today.atTime(9, 50),
            )
            val c = completion("c-C", "t-C", start = today.atTime(10, 0), end = today.atTime(10, 30))

            val result = calculator(
                listOf(a, b, c),
                listOf(task("t-A"), task("t-B"), task("t-C")),
                today, now,
            )
            assertEquals(today.atTime(9, 30), result.single { it.id == "c-B" }.plannedStart)  // B 不动
            assertEquals(today.atTime(10, 0), result.single { it.id == "c-C" }.plannedStart)  // C 不动
            assertTrue(result.single { it.id == "c-C" }.affectedTasks.isEmpty())
        }
    }

    @Nested
    @DisplayName("跨计划 / 跨日")
    inner class CrossPlanAndDate {
        @Test
        fun `跨 plan 任务按绝对时间混排 — A_planX 推 B_planY`() {
            // A 9:00-9:30 IN_PROGRESS actualEnd=9:45 (planA)
            // B 9:30-10:00 PENDING (planB)
            // 期望：B 被 A 推
            val a = completion(
                "c-A", "t-A", planId = planA,
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(9, 45),
            )
            val b = completion("c-B", "t-B", planId = planB, start = today.atTime(9, 30), end = today.atTime(10, 0))
            val taskA = Task(
                id = "t-A", planId = planA, title = "A",
                startMinutes = 9 * 60, endMinutes = 9 * 60 + 30,
                repeatRule = RepeatRule.DAILY, isLocked = false, orderInPlan = 0,
            )
            val taskB = Task(
                id = "t-B", planId = planB, title = "B",
                startMinutes = 9 * 60 + 30, endMinutes = 10 * 60,
                repeatRule = RepeatRule.DAILY, isLocked = false, orderInPlan = 0,
            )

            val result = calculator(listOf(a, b), listOf(taskA, taskB), today, now)
            val outB = result.single { it.id == "c-B" }
            assertEquals(today.atTime(9, 45), outB.plannedStart)
            assertEquals(today.atTime(10, 15), outB.plannedEnd)
            assertEquals(listOf("t-A"), outB.affectedTasks)
        }

        @Test
        fun `不影响明天 — 明天同 plan 同时间窗的 completion 不动`() {
            // 今天 A 9:00-9:30 IN_PROGRESS actualEnd=10:00
            // 今天 B 9:30-10:00 PENDING → 推到 10:00-10:30
            // 明天 C 9:00-9:30 PENDING (相同时间窗的明日实例) → 不动
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(10, 0),
            )
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))
            val c = completion(
                "c-C", "t-A",  // 同 task，明天的同任务实例
                start = tomorrow.atTime(9, 0), end = tomorrow.atTime(9, 30),
                scheduledDate = tomorrow,
            )

            val result = calculator(
                listOf(a, b, c),
                listOf(task("t-A"), task("t-B")),
                today, now,
            )
            // 今天的 B 推
            assertEquals(today.atTime(10, 0), result.single { it.id == "c-B" }.plannedStart)
            // 明天的 C 不动
            assertEquals(tomorrow.atTime(9, 0), result.single { it.id == "c-C" }.plannedStart)
            assertEquals(tomorrow.atTime(9, 30), result.single { it.id == "c-C" }.plannedEnd)
        }

        @Test
        fun `输入列表顺序无关 — 乱序输入按 computedStartAt 排后正确处理`() {
            // A IN_PROGRESS 9:00-9:30 actualEnd=9:45
            // B PENDING 9:30-10:00
            // 输入乱序 [B, A] → 仍应正确推 B
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(9, 45),
            )
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))

            val result = calculator(
                listOf(b, a),  // 故意乱序
                listOf(task("t-A"), task("t-B")),
                today, now,
            )
            val outB = result.single { it.id == "c-B" }
            assertEquals(today.atTime(9, 45), outB.plannedStart)
            assertEquals(listOf("t-A"), outB.affectedTasks)
        }
    }

    @Nested
    @DisplayName("边界")
    inner class Edge {
        @Test
        fun `空 completions — 返回空列表`() {
            val result = calculator(emptyList(), emptyList(), today, now)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `输入列表不被修改（不变量）`() {
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(9, 45),
            )
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))
            val originalA = a.copy()
            val originalB = b.copy()
            calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), today, now)
            assertEquals(originalA, a)
            assertEquals(originalB, b)
        }

        @Test
        fun `prev_computedEndAt 等于 cur_computedStartAt — 不算碰撞（边界）`() {
            // A IN_PROGRESS 9:00-9:30 actualEnd=9:30（刚好不超时）
            // B PENDING 9:30-10:00
            // expectedEnd = curStart → isAfter = false → 不推
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(9, 0), end = today.atTime(9, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(9, 0),
                actualEnd = today.atTime(9, 30),
            )
            val b = completion("c-B", "t-B", start = today.atTime(9, 30), end = today.atTime(10, 0))
            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), today, now)
            assertEquals(today.atTime(9, 30), result.single { it.id == "c-B" }.plannedStart)
        }

        @Test
        fun `跨午夜 IN_PROGRESS — actualEnd 跨日，下游自动跨日`() {
            // A 23:00-23:30 IN_PROGRESS actualEnd=次日 0:30
            // B 23:30-24:00 PENDING
            val a = completion(
                "c-A", "t-A",
                start = today.atTime(23, 0), end = today.atTime(23, 30),
                status = TaskStatus.IN_PROGRESS,
                actualStart = today.atTime(23, 0),
                actualEnd = tomorrow.atTime(0, 30),
            )
            val b = completion("c-B", "t-B", start = today.atTime(23, 30), end = tomorrow.atTime(0, 0))
            val result = calculator(listOf(a, b), listOf(task("t-A"), task("t-B")), today, now)
            val outB = result.single { it.id == "c-B" }
            // B 从 23:30 推到次日 0:30
            assertEquals(tomorrow.atTime(0, 30), outB.plannedStart)
            assertEquals(tomorrow.atTime(1, 0), outB.plannedEnd)
            assertEquals(listOf("t-A"), outB.affectedTasks)
        }
    }
}
