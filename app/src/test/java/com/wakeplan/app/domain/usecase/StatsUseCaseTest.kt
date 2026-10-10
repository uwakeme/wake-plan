package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * StatsUseCase 聚合逻辑 — Phase 5.2.1，v0.4.1 分母改为任务定义推导
 */
@DisplayName("StatsUseCase")
class StatsUseCaseTest {

    private val useCase = StatsUseCase(TaskWindowCalculator())
    private val today = LocalDate.of(2026, 8, 8)

    private fun plan(
        id: String,
        title: String = "Plan $id",
        color: String = "#3B6FB5",
        archived: Boolean = false,
    ) = Plan(
        id = id,
        title = title,
        colorHex = color,
        startDate = LocalDate.of(2026, 1, 1),
        isActive = true,
        archived = archived,
        createdAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun task(
        id: String = "task-1",
        planId: String = "plan-1",
        rule: RepeatRule = RepeatRule.DAILY,
        createdEpochDay: Long = 0L,
    ) = Task(
        id = id,
        planId = planId,
        title = "Task $id",
        startMinutes = 9 * 60,
        endMinutes = 10 * 60,
        repeatRule = rule,
        createdEpochDay = createdEpochDay,
        orderInPlan = 0,
    )

    private fun completion(
        id: String = "c-${System.nanoTime()}-${Math.random()}",
        taskId: String = "task-1",
        planId: String = "plan-1",
        date: LocalDate = today,
        start: LocalDateTime = date.atTime(9, 0),
        end: LocalDateTime = date.atTime(9, 30),
        status: TaskStatus = TaskStatus.COMPLETED,
        overrun: Int = 0,
    ) = TaskCompletion(
        id = id,
        taskId = taskId,
        planId = planId,
        scheduledDate = date,
        plannedStart = start,
        plannedEnd = end,
        actualStart = start,
        actualEnd = end.plusMinutes(overrun.toLong()),
        status = status,
        overrunMinutes = overrun,
    )

    @Test
    fun `空输入 — 全部 0 且 perDay 长度等于 range 的 days`() {
        val r = useCase.compute(emptyList(), emptyList(), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(0, r.totalCompleted)
        assertEquals(0, r.totalSkipped)
        assertEquals(0, r.totalPending)
        assertEquals(0, r.onTimeCount)
        assertEquals(0, r.avgOverrunMinutes)
        assertEquals(0f, r.onTimeRate)
        assertEquals(0f, r.completionRate)
        assertEquals(7, r.perDay.size)
        assertTrue(r.perDay.all { it.completedCount == 0 })
        assertTrue(r.perPlan.isEmpty())
    }

    @Test
    fun `7 天窗口 — perDay 7 项 + startDate 和 endDate 正确`() {
        val r = useCase.compute(emptyList(), emptyList(), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(today.minusDays(6), r.startDate)
        assertEquals(today, r.endDate)
    }

    @Test
    fun `归档计划的任务不进完成率分母`() {
        // 归档计划不再铺实例、不再产生完成记录，留在分母里会无声拉低完成率
        val plans = listOf(plan("plan-1"), plan("plan-archived", archived = true))
        val tasks = listOf(
            task(id = "task-active", planId = "plan-1"),
            task(id = "task-archived", planId = "plan-archived"),
        )
        val completions = listOf(
            completion(id = "c-1", taskId = "task-active", planId = "plan-1"),
        )

        // 只看今天：分母应为 1（仅活跃计划的任务），归档计划的 1 个任务被排除
        val r = useCase.compute(
            completions, plans, tasks,
            StatsUseCase.Range.LAST_7_DAYS, today,
        )
        val todayBucket = r.perDay.last()

        assertEquals(1, todayBucket.expectedCount)
        // 整天窗口下活跃计划每天各贡献 1，归档计划 0
        assertEquals(7, r.totalExpected)
        assertEquals(1, r.totalCompleted)
        assertEquals(1f / 7f, r.completionRate, 0.0001f)
    }

    @Test
    fun `归档计划的历史完成仍计入分子`() {
        // 归档是停止未来排期，不是抹除历史——分子保留既有完成
        val plans = listOf(plan("plan-1"), plan("plan-archived", archived = true))
        val tasks = listOf(task(id = "task-archived", planId = "plan-archived"))
        val completions = listOf(
            completion(id = "c-old", taskId = "task-archived", planId = "plan-archived"),
        )

        val r = useCase.compute(completions, plans, tasks, StatsUseCase.Range.LAST_7_DAYS, today)

        assertEquals(1, r.totalCompleted)
        assertEquals(0, r.totalExpected)
    }

    @Test
    fun `30 天窗口 — perDay 30 项`() {
        val r = useCase.compute(emptyList(), emptyList(), emptyList(), StatsUseCase.Range.LAST_30_DAYS, today)
        assertEquals(30, r.perDay.size)
        assertEquals(today.minusDays(29), r.startDate)
    }

    @Test
    fun `perDay 补齐 — 范围内没有 completion 的日期 count=0`() {
        val r = useCase.compute(emptyList(), emptyList(), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertTrue(r.perDay.all { it.completedCount == 0 && it.skippedCount == 0 && it.pendingCount == 0 })
    }

    @Test
    fun `范围内 completion 按天分组`() {
        val cs = listOf(
            completion(date = today, status = TaskStatus.COMPLETED),
            completion(date = today, status = TaskStatus.COMPLETED),
            completion(date = today.minusDays(1), status = TaskStatus.COMPLETED),
            completion(date = today.minusDays(5), status = TaskStatus.SKIPPED),
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(2, r.perDay[6].completedCount)
        assertEquals(1, r.perDay[5].completedCount)
        assertEquals(1, r.perDay[1].skippedCount)
        assertEquals(3, r.totalCompleted)
        assertEquals(1, r.totalSkipped)
    }

    @Test
    fun `范围外的 completion 不算`() {
        val cs = listOf(
            completion(date = today.plusDays(1), status = TaskStatus.COMPLETED),  // 范围外
            completion(date = today.minusDays(10), status = TaskStatus.COMPLETED),  // 范围外
            completion(date = today, status = TaskStatus.COMPLETED),  // 范围内
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(1, r.totalCompleted)
    }

    @Test
    fun `准时 — overrun 0-2 分钟都算 on-time`() {
        val cs = listOf(
            completion(overrun = 0),
            completion(overrun = 1),
            completion(overrun = 2),
            completion(overrun = 3),   // 算逾期
            completion(overrun = 5),   // 算逾期
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(3, r.onTimeCount)
        assertEquals(0.6f, r.onTimeRate, 0.001f)
    }

    @Test
    fun `平均延期 — 只算 completed 且 overrun 大于 0 的（全局与 perDay 同口径）`() {
        val cs = listOf(
            completion(overrun = 0),       // 准时，不算进 avg
            completion(overrun = 5),       // 算
            completion(overrun = 10),      // 算
            completion(overrun = 15),      // 算
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        // avg = (5+10+15)/3 = 10
        assertEquals(10, r.avgOverrunMinutes)
        // perDay 与全局同口径（v0.4.1 统一：只对 overrun>0 求均值）
        assertEquals(10, r.perDay[6].avgOverrunMinutes)
    }

    @Test
    fun `提前完成 — overrun 为负数 on-time 算 且 avg 只算正数不稀释`() {
        val cs = listOf(
            completion(overrun = -5),  // 提前 5 分钟
            completion(overrun = 0),
            completion(overrun = 10),
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        // 三个都 ≤ 2 的算 on-time：-5 与 0（10 不算）
        assertEquals(2, r.onTimeCount)
        // avg 只算 overrun>0：唯一正数 10 → 10（v0.4.1 不会被提前/准时拉低）
        assertEquals(10, r.avgOverrunMinutes)
    }

    @Test
    fun `PENDING 跟 IN_PROGRESS 都算 pending`() {
        val cs = listOf(
            completion(status = TaskStatus.PENDING),
            completion(status = TaskStatus.IN_PROGRESS),
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(0, r.totalCompleted)
        assertEquals(2, r.totalPending)
    }

    @Test
    fun `perPlan 按计划分组 按 completedCount 降序`() {
        val plans = listOf(
            plan("plan-1", "晨练", "#C48520"),
            plan("plan-2", "学习", "#3B6FB5"),
            plan("plan-3", "副业", "#8B5CF6"),
        )
        val cs = listOf(
            completion(planId = "plan-1", status = TaskStatus.COMPLETED),
            completion(planId = "plan-1", status = TaskStatus.COMPLETED),
            completion(planId = "plan-1", status = TaskStatus.COMPLETED),
            completion(planId = "plan-2", status = TaskStatus.COMPLETED),
        )
        val r = useCase.compute(cs, plans, emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(2, r.perPlan.size)
        assertEquals("plan-1", r.perPlan[0].planId)
        assertEquals(3, r.perPlan[0].completedCount)
        assertEquals("晨练", r.perPlan[0].planTitle)
        assertEquals("#C48520", r.perPlan[0].colorHex)
        assertEquals("plan-2", r.perPlan[1].planId)
        assertEquals(1, r.perPlan[1].completedCount)
    }

    @Test
    fun `perPlan — plan 已被删除的 completion 算到 (已删除计划) placeholder`() {
        val cs = listOf(
            completion(planId = "deleted-plan", status = TaskStatus.COMPLETED),
        )
        val r = useCase.compute(cs, emptyList(), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(1, r.perPlan.size)
        assertEquals("deleted-plan", r.perPlan[0].planId)
        assertEquals("(已删除计划)", r.perPlan[0].planTitle)
        assertEquals("#999999", r.perPlan[0].colorHex)
    }

    @Test
    fun `completionRate — 0 完成返回 0 不是 NaN`() {
        val cs = listOf(
            completion(status = TaskStatus.SKIPPED),
            completion(status = TaskStatus.PENDING),
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(0f, r.completionRate)
        assertEquals(0f, r.onTimeRate)
    }

    @Test
    fun `perDay bucket — 当天所有状态分别统计`() {
        val cs = listOf(
            completion(status = TaskStatus.COMPLETED),
            completion(status = TaskStatus.COMPLETED),
            completion(status = TaskStatus.SKIPPED),
            completion(status = TaskStatus.PENDING),
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.LAST_7_DAYS, today)
        val todayBucket = r.perDay[6]
        assertEquals(2, todayBucket.completedCount)
        assertEquals(1, todayBucket.skippedCount)
        assertEquals(1, todayBucket.pendingCount)
    }

    // ---- v0.4.1 分母改为任务定义推导 ----

    @Test
    fun `分母按任务定义推导 — 没打开 App 的日子不再被稀释成 0 percent`() {
        // 1 个 DAILY 任务 × 7 天 = expected 7；只完成 3 次（其余天根本没实例）
        val tasks = listOf(task())
        val cs = listOf(
            completion(date = today),
            completion(date = today.minusDays(1)),
            completion(date = today.minusDays(2)),
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), tasks, StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(7, r.totalExpected)
        assertEquals(3, r.totalCompleted)
        assertEquals(3f / 7f, r.completionRate, 0.0001f)
        assertEquals(1, r.perDay[0].expectedCount)
    }

    @Test
    fun `WEEKDAYS 任务只在工作日计入分母`() {
        val tasks = listOf(task(rule = RepeatRule.WEEKDAYS))
        // 2026-08-08 是周六：窗口 [8/2(日) .. 8/8(六)]，工作日 = 8/3,4,5,6,7 共 5 天
        val r = useCase.compute(emptyList(), listOf(plan("plan-1")), tasks, StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(5, r.totalExpected)
    }

    @Test
    fun `创建晚于某日的任务不计入该日分母（createdEpochDay 限制）`() {
        // 任务 8/7 才创建（epochDay），8/2~8/6 不该出现在分母里
        val createdEpochDay = LocalDate.of(2026, 8, 7).toEpochDay()
        val tasks = listOf(task(createdEpochDay = createdEpochDay))
        val r = useCase.compute(emptyList(), listOf(plan("plan-1")), tasks, StatsUseCase.Range.LAST_7_DAYS, today)
        // 窗口 [8/2 .. 8/8]，任务只覆盖 8/7、8/8 两天
        assertEquals(2, r.totalExpected)
    }

    @Test
    fun `计划开始日之前任务不计入分母`() {
        val latePlan = plan("plan-1").copy(startDate = today)  // 今天才开始
        val tasks = listOf(task())
        val r = useCase.compute(emptyList(), listOf(latePlan), tasks, StatsUseCase.Range.LAST_7_DAYS, today)
        assertEquals(1, r.totalExpected)
    }

    // ---- v0.5 THIS_MONTH 自然月窗口 ----

    @Test
    fun `THIS_MONTH 窗口从月首到今天 — 8 月 8 日窗口 8 天`() {
        val r = useCase.compute(emptyList(), emptyList(), emptyList(), StatsUseCase.Range.THIS_MONTH, today)
        assertEquals(LocalDate.of(2026, 8, 1), r.startDate)
        assertEquals(today, r.endDate)
        assertEquals(8, r.perDay.size)
    }

    @Test
    fun `THIS_MONTH 月中 — 15 日窗口 15 天，月首之前的 completion 不算`() {
        val mid = LocalDate.of(2026, 8, 15)
        val cs = listOf(
            completion(date = LocalDate.of(2026, 7, 31)),  // 上月，窗口外
            completion(date = LocalDate.of(2026, 8, 1)),
            completion(date = mid),
        )
        val r = useCase.compute(cs, listOf(plan("plan-1")), emptyList(), StatsUseCase.Range.THIS_MONTH, mid)
        assertEquals(15, r.perDay.size)
        assertEquals(2, r.totalCompleted)
    }

    @Test
    fun `THIS_MONTH 月末 31 天月 — perDay 长度 31`() {
        val end = LocalDate.of(2026, 8, 31)  // 8 月 31 天
        val r = useCase.compute(emptyList(), emptyList(), emptyList(), StatsUseCase.Range.THIS_MONTH, end)
        assertEquals(31, r.perDay.size)
    }

    @Test
    fun `THIS_MONTH 月 1 日 — 窗口仅 1 天`() {
        val first = LocalDate.of(2026, 8, 1)
        val r = useCase.compute(emptyList(), emptyList(), emptyList(), StatsUseCase.Range.THIS_MONTH, first)
        assertEquals(1, r.perDay.size)
        assertEquals(first, r.startDate)
    }

    // ---- v0.6 专注分钟 ----

    @Test
    fun `专注分钟按天与计划归档 — FINISHED 与 ABANDONED 都计入`() {
        val entries = listOf(
            StatsUseCase.FocusEntry(date = today, planId = "plan-1", minutes = 25),
            StatsUseCase.FocusEntry(date = today, planId = "plan-1", minutes = 10),   // 放弃的轮
            StatsUseCase.FocusEntry(date = today.minusDays(1), planId = "plan-2", minutes = 30),
            StatsUseCase.FocusEntry(date = today.plusDays(1), planId = "plan-1", minutes = 99),  // 窗口外
        )
        val r = useCase.compute(
            emptyList(), listOf(plan("plan-1"), plan("plan-2")), emptyList(),
            StatsUseCase.Range.LAST_7_DAYS, today, entries,
        )
        assertEquals(65, r.totalFocusMinutes)                       // 25+10+30
        assertEquals(35, r.perDay[6].focusMinutes)                  // 今天
        assertEquals(30, r.perDay[5].focusMinutes)                  // 昨天
        val p1 = r.perPlan.firstOrNull { it.planId == "plan-1" }
        // plan-1 无 completed completion → 不进 perPlan（专注只在已有桶上加，不造桶）
        org.junit.jupiter.api.Assertions.assertNull(p1)
    }

    @Test
    fun `无专注数据 — 全部为 0（默认参数向后兼容）`() {
        val r = useCase.compute(
            listOf(completion(status = TaskStatus.COMPLETED)),
            listOf(plan("plan-1")), emptyList(),
            StatsUseCase.Range.LAST_7_DAYS, today,
        )
        assertEquals(0, r.totalFocusMinutes)
        assertTrue(r.perDay.all { it.focusMinutes == 0 })
    }

    @Test
    fun `窗口口径一致性 — LAST_30_DAYS 仍是滚动 30 天`() {
        val r = useCase.compute(emptyList(), emptyList(), emptyList(), StatsUseCase.Range.LAST_30_DAYS, today)
        assertEquals(today.minusDays(29), r.startDate)
        assertEquals(30, r.perDay.size)
    }
}
