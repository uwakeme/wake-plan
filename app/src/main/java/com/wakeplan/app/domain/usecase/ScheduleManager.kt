package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 调度计划 — ScheduleManager 输出，StartingAlarmScheduler / OverdueWorkScheduler 消费
 *
 * 注意：v0.1 overdue 简化处理：OverdueCheckWorker 自己查 plannedEnd < now 的 completion，
 * 不依赖 SchedulePlan.overdueIds。overdueIds 留作 v0.2 精细化（指定 completion 提醒）
 */
data class SchedulePlan(
    /** 待调度 starting alarm 的 (completionId, triggerAt) 列表（triggerAt 已是绝对 LocalDateTime） */
    val startingTriggers: List<StartingTrigger>,
)

data class StartingTrigger(
    val completionId: String,
    val triggerAt: LocalDateTime,
)

/**
 * 调度计算 — Phase 3.2 核心 UseCase
 *
 * 输入：当日所有 completion + 任务定义 + 当前时间 + lead minutes
 * 输出：SchedulePlan
 *
 * 规则：
 * - 只调度 status=PENDING 的 completion（IN_PROGRESS/COMPLETED/SKIPPED 不再调度）
 * - triggerAt = plannedStart - leadMinutes
 * - 如果 triggerAt < now（已过期或即将发生）— 仍 schedule，让 AlarmManager 立即触发
 * - 如果 triggerAt > now + 24h — 跳过（明天再 schedule）
 *
 * v0.1 不支持跨日 schedule（明天 0:00 重新跑 ensureTodayCompletions + scheduleAll）
 */
class ScheduleManager @Inject constructor() {

    operator fun invoke(
        completions: List<TaskCompletion>,
        tasks: List<Task>,
        now: LocalDateTime,
        leadMinutes: Int,
        startingEnabled: Boolean = true,
    ): SchedulePlan {
        if (!startingEnabled) return SchedulePlan(emptyList())
        val taskById = tasks.associateBy { it.id }

        val triggers = completions
            .asSequence()
            .filter { it.status == TaskStatus.PENDING }
            .mapNotNull { c ->
                val task = taskById[c.taskId] ?: return@mapNotNull null
                // 段当前日期 plannedStart - lead
                val triggerAt = c.plannedStart.minusMinutes(leadMinutes.toLong())
                // 跨午夜：c.plannedStart 可能 > now + 24h（明天很晚的任务），跳过
                if (triggerAt.isAfter(now.plusHours(24))) return@mapNotNull null
                StartingTrigger(c.id, triggerAt)
            }
            .sortedBy { it.triggerAt }
            .toList()
        return SchedulePlan(triggers)
    }
}
