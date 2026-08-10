package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 完成统计 — Phase 5.2.1
 *
 * 输入：某时间段内所有 completion + 全量 plan 列表（用于展示分布）
 * 输出：聚合数字 + 按天 / 按计划分布
 *
 * 准时定义（PRD 5.3 + 行业惯例）：完成时间跟 plannedEnd 相差 ≤ 2 分钟
 * 延期 = actualEnd > plannedEnd（正数）
 * 提前 = actualEnd < plannedEnd（负数，计入"完成"但不算准时）
 *
 * 跟 TaskCompletion.overrunMinutes 的区别：
 * - `overrunMinutes` = actualEnd - plannedEnd（正数向下取整）
 * - 统计里"平均延期" = completed 且 overrunMinutes > 2 的 completion 的 overrunMinutes 平均
 */
class StatsUseCase @Inject constructor() {

    enum class Range(val days: Int) {
        LAST_7_DAYS(7),
        LAST_30_DAYS(30),
    }

    /**
     * 每天统计
     */
    data class DayBucket(
        val date: LocalDate,
        val completedCount: Int,
        val skippedCount: Int,
        val pendingCount: Int,
        val avgOverrunMinutes: Int,    // 当天已完成任务的平均 overrun（min），无则为 0
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
        val onTimeCount: Int,
        val avgOverrunMinutes: Int,    // 仅 completed 且 overrun > 0 的平均，0 表示全准时或无完成
        val perDay: List<DayBucket>,   // 按 date 升序，长度 = range.days
        val perPlan: List<PlanBucket>, // 按 completedCount 降序
    ) {
        /**
         * 准时率（0..1）— onTime / totalCompleted
         * 0 完成时返回 0（不是 NaN）
         */
        val onTimeRate: Float
            get() = if (totalCompleted == 0) 0f else onTimeCount.toFloat() / totalCompleted

        /**
         * 完成率（0..1）— completed / (completed + skipped + pending)
         * 用于顶部大数字
         */
        val completionRate: Float
            get() {
                val total = totalCompleted + totalSkipped + totalPending
                return if (total == 0) 0f else totalCompleted.toFloat() / total
            }
    }

    /**
     * 计算统计
     * @param today 用于"最近 N 天"窗口的今天（注入便于测试）
     */
    fun compute(
        completions: List<TaskCompletion>,
        plans: List<Plan>,
        range: Range,
        today: LocalDate,
    ): StatsResult {
        val start = today.minusDays((range.days - 1).toLong())
        val end = today

        val inRange = completions.filter { c ->
            val d = c.scheduledDate
            !d.isBefore(start) && !d.isAfter(end)
        }
        val inRangeByDate: Map<LocalDate, List<TaskCompletion>> = inRange.groupBy { it.scheduledDate }

        // per-day buckets（补齐没有数据的日期，count=0）
        val perDay = (0 until range.days).map { offset ->
            val date = start.plusDays(offset.toLong())
            val dayList = inRangeByDate[date].orEmpty()
            val completed = dayList.filter { it.status == TaskStatus.COMPLETED }
            val avgOverrun = if (completed.isEmpty()) 0
            else completed.map { it.overrunMinutes }.average().roundToInt()
            DayBucket(
                date = date,
                completedCount = dayList.count { it.status == TaskStatus.COMPLETED },
                skippedCount = dayList.count { it.status == TaskStatus.SKIPPED },
                pendingCount = dayList.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS },
                avgOverrunMinutes = avgOverrun,
            )
        }

        // per-plan buckets
        val plansById = plans.associateBy { it.id }
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
                )
            }
            .sortedByDescending { it.completedCount }

        val completed = inRange.filter { it.status == TaskStatus.COMPLETED }
        val onTime = completed.count { it.overrunMinutes <= ON_TIME_THRESHOLD_MIN }
        // 只算 overrun > 0 的（准时的/提前的拉到 0/负数会拉低平均，意义不大）
        val withOverrun = completed.filter { it.overrunMinutes > 0 }
        val avgOverrunAll = if (withOverrun.isEmpty()) 0
        else withOverrun.map { it.overrunMinutes }.average().roundToInt()

        return StatsResult(
            range = range,
            startDate = start,
            endDate = end,
            totalCompleted = completed.size,
            totalSkipped = inRange.count { it.status == TaskStatus.SKIPPED },
            totalPending = inRange.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS },
            onTimeCount = onTime,
            avgOverrunMinutes = avgOverrunAll,
            perDay = perDay,
            perPlan = perPlan,
        )
    }

    companion object {
        const val ON_TIME_THRESHOLD_MIN = 2
    }
}
