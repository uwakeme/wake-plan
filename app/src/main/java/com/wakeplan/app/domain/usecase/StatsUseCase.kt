package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 完成统计 — Phase 5.2.1，v0.4.1 分母改为任务定义推导
 *
 * 输入：某时间段内所有 completion + 全量 plan / task 列表
 * 输出：聚合数字 + 按天 / 按计划分布
 *
 * v0.4.1 关键修正——完成率分母：
 * 旧版用"已实例化 completion 数"当分母，某天没打开 App 就没有实例，
 * 完成率被稀释成 0%。现在分母 = 当天应生效任务数（按 task 定义 + repeatRule
 * 推导，且排除计划起始日之前 / 任务创建日之后的天）。已删除的任务/计划不在
 * 分母里（其历史完成仍在分子，近似口径，已注释标注）。
 *
 * 准时定义（PRD 5.3 + 行业惯例）：完成时间跟 plannedEnd 相差 ≤ 2 分钟
 * 平均延期：completed 且 overrunMinutes > 0 的平均（perDay 与全局同口径）
 */
class StatsUseCase @Inject constructor(
    private val taskWindowCalculator: TaskWindowCalculator,
) {

    /**
     * 统计窗口 — v0.5 加 THIS_MONTH（自然月），模型从"近 N 天"改为窗口推导
     */
    enum class Range {
        LAST_7_DAYS,
        LAST_30_DAYS,
        THIS_MONTH,
        ;

        /** 含首尾的统计窗口（围绕 today） */
        fun window(today: LocalDate): ClosedRange<LocalDate> = when (this) {
            LAST_7_DAYS -> today.minusDays(6)..today
            LAST_30_DAYS -> today.minusDays(29)..today
            THIS_MONTH -> today.withDayOfMonth(1)..today
        }

        /** 窗口天数（含首尾） */
        fun daysInWindow(today: LocalDate): Int {
            val w = window(today)
            return java.time.temporal.ChronoUnit.DAYS.between(w.start, w.endInclusive).toInt() + 1
        }
    }

    /**
     * 专注轮的按天归档输入（v0.6）——VM 把 FocusSession 按时区折算成天后传入，
     * UseCase 保持纯函数不依赖时区
     */
    data class FocusEntry(
        val date: LocalDate,
        val planId: String,
        /** 实际专注分钟（FINISHED 与 ABANDONED 都计入——放弃也专注了） */
        val minutes: Int,
    )

    /**
     * 每天统计
     */
    data class DayBucket(
        val date: LocalDate,
        val completedCount: Int,
        val skippedCount: Int,
        val pendingCount: Int,
        /** 当天应生效任务数（按定义推导）——完成率的分母 */
        val expectedCount: Int,
        val avgOverrunMinutes: Int,    // 当天 completed 且 overrun>0 的平均 overrun（min），无则为 0
        /** 当天实际专注分钟（v0.6） */
        val focusMinutes: Int = 0,
    ) {
        val totalCount: Int get() = completedCount + skippedCount + pendingCount
    }

    /**
     * 按计划统计
     */
    data class PlanBucket(
        val planId: String,
        val planTitle: String,
        val colorHex: String,
        val completedCount: Int,
        /** 该计划窗口内实际专注分钟（v0.6） */
        val focusMinutes: Int = 0,
    )

    /**
     * 完整统计结果
     */
    data class StatsResult(
        val range: Range,
        val startDate: LocalDate,
        val endDate: LocalDate,    // 包含
        val totalCompleted: Int,
        val totalSkipped: Int,
        val totalPending: Int,
        /** 窗口内每天应生效任务数之和——完成率的分母 */
        val totalExpected: Int,
        /** 窗口内实际专注分钟之和（v0.6） */
        val totalFocusMinutes: Int = 0,
        val onTimeCount: Int,
        val avgOverrunMinutes: Int,    // 仅 completed 且 overrun > 0 的平均，0 表示全准时或无完成
        val perDay: List<DayBucket>,   // 按 date 升序，长度 = 窗口天数
        val perPlan: List<PlanBucket>, // 按 completedCount 降序
    ) {
        /**
         * 准时率（0..1）— onTime / totalCompleted
         * 0 完成时返回 0（不是 NaN）
         */
        val onTimeRate: Float
            get() = if (totalCompleted == 0) 0f else onTimeCount.toFloat() / totalCompleted

        /**
         * 完成率（0..1）— completed / expected（按任务定义推导的应生效数）。
         * 分母为 0 返回 0。注意：已删除计划的历史完成仍在分子但不在分母，
         * 极端情况下比率可能 > 1，UI 层 coerce 到 1。
         */
        val completionRate: Float
            get() = if (totalExpected == 0) 0f else totalCompleted.toFloat() / totalExpected
    }

    /**
     * 计算统计
     * @param tasks 全量任务定义（推导每天应生效数）
     * @param today 用于"最近 N 天"窗口的今天（注入便于测试）
     */
    fun compute(
        completions: List<TaskCompletion>,
        plans: List<Plan>,
        tasks: List<Task>,
        range: Range,
        today: LocalDate,
        focusSessions: List<FocusEntry> = emptyList(),
    ): StatsResult {
        val window = range.window(today)
        val start = window.start
        val end = window.endInclusive
        val windowDays = range.daysInWindow(today)

        val inRange = completions.filter { c ->
            val d = c.scheduledDate
            !d.isBefore(start) && !d.isAfter(end)
        }
        val inRangeByDate: Map<LocalDate, List<TaskCompletion>> = inRange.groupBy { it.scheduledDate }

        val plansById = plans.associateBy { it.id }

        // per-day buckets（补齐没有数据的日期，count=0）
        val focusByDate: Map<LocalDate, Int> = focusSessions
            .filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.minutes } }

        val perDay = (0 until windowDays).map { offset ->
            val date = start.plusDays(offset.toLong())
            dayBucket(inRangeByDate[date].orEmpty(), plansById, tasks, date)
                .copy(focusMinutes = focusByDate[date] ?: 0)
        }

        val focusByPlan: Map<String, Int> = focusSessions
            .filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            .groupBy { it.planId }
            .mapValues { (_, list) -> list.sumOf { it.minutes } }

        // per-plan buckets（plansById 复用上面的）
        val perPlan = inRange
            .filter { it.status == TaskStatus.COMPLETED }
            .groupBy { it.planId }
            .map { (planId, list) ->
                val plan = plansById[planId]
                PlanBucket(
                    planId = planId,
                    planTitle = plan?.title ?: "(已删除计划)",
                    colorHex = plan?.colorHex ?: "#999999",
                    completedCount = list.size,
                    focusMinutes = focusByPlan[planId] ?: 0,
                )
            }
            .sortedByDescending { it.completedCount }

        val completed = inRange.filter { it.status == TaskStatus.COMPLETED }
        val onTime = completed.count { it.overrunMinutes <= ON_TIME_THRESHOLD_MIN }

        return StatsResult(
            range = range,
            startDate = start,
            endDate = end,
            totalCompleted = completed.size,
            totalSkipped = inRange.count { it.status == TaskStatus.SKIPPED },
            totalPending = inRange.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS },
            totalExpected = perDay.sumOf { it.expectedCount },
            totalFocusMinutes = focusByDate.values.sum(),
            onTimeCount = onTime,
            avgOverrunMinutes = avgOverrunOf(completed),
            perDay = perDay,
            perPlan = perPlan,
        )
    }

    /** 平均延期：只对 overrun > 0 的完成求均值（perDay 与全局同口径），无则为 0 */
    private fun avgOverrunOf(completed: List<TaskCompletion>): Int {
        val withOverrun = completed.filter { it.overrunMinutes > 0 }
        if (withOverrun.isEmpty()) return 0
        return withOverrun.map { it.overrunMinutes }.average().roundToInt()
    }

    /**
     * 单日小结（v0.5：RecapWorker 每日回顾复用；compute 的 perDay 同源）。
     * @param completions 传该日实例即可（内部不再按日期过滤）
     */
    fun dayBucket(
        completions: List<TaskCompletion>,
        plans: List<Plan>,
        tasks: List<Task>,
        date: LocalDate,
    ): DayBucket {
        val plansById = plans.associateBy { it.id }
        return dayBucket(completions, plansById, tasks, date)
    }

    private fun dayBucket(
        dayList: List<TaskCompletion>,
        plansById: Map<String, Plan>,
        tasks: List<Task>,
        date: LocalDate,
    ): DayBucket {
        val completed = dayList.filter { it.status == TaskStatus.COMPLETED }
        // 分母：当天应生效的任务数（计划已开始、任务已创建、且计划未归档）
        // 归档计划不再铺实例、不再产生完成记录，留在分母里会无声拉低完成率
        // （与 TodayViewModel 的 `!archived` 过滤保持同一口径）
        // 已归档计划的历史完成仍计入分子——归档停止的是未来排期，不是抹除历史
        val expected = tasks.count { task ->
            val plan = plansById[task.planId]
            plan != null && !plan.archived && !plan.startDate.isAfter(date) &&
                (task.createdEpochDay == 0L || task.createdEpochDay <= date.toEpochDay()) &&
                taskWindowCalculator(task, date) != null
        }
        return DayBucket(
            date = date,
            completedCount = completed.size,
            skippedCount = dayList.count { it.status == TaskStatus.SKIPPED },
            pendingCount = dayList.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS },
            expectedCount = expected,
            avgOverrunMinutes = avgOverrunOf(completed),
        )
    }

    companion object {
        const val ON_TIME_THRESHOLD_MIN = 2
    }
}
