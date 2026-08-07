package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.model.TimeSegment
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * 把"段 + 任务 + 日期"展开为 TaskCompletion 列表（PENDING 状态）
 *
 * 段内排程规则（v0.1）：
 * - 段 window = [plannedStart, plannedEnd)
 * - 段内任务按时长累加，从 plannedStart 起顺序铺
 * - 任务总时长超出段边界时，超出部分延到段外（plannedEnd > 段 plannedEnd）
 * - AutoExtendCalculator（Phase 2.2）会基于已有 completion 改写 plannedStart
 *
 * 注意：本 UseCase 是"无状态"展开（输入是纯快照），每次调用结果一致；
 * UI 层通过 TaskCompletionRepository.upsert 去重（OnConflictStrategy.REPLACE）
 */
class TaskCompletionInstantiator @Inject constructor(
    private val timeWindowCalculator: TimeWindowCalculator,
) {

    /**
     * @param plan 仅用于填 planId
     * @param segments 段列表（已按 planId 过滤）
     * @param tasks 任务列表（已按 planId 过滤，跨段无序传入）
     * @param date 要展开的日期
     * @param idGenerator 测试时可注入（默认 UUID.randomUUID）
     */
    operator fun invoke(
        plan: Plan,
        segments: List<TimeSegment>,
        tasks: List<Task>,
        date: LocalDate,
        idGenerator: () -> String = { UUID.randomUUID().toString() },
    ): List<TaskCompletion> {
        return segments
            .mapNotNull { segment -> segment to timeWindowCalculator(segment, date) }
            .filter { (_, window) -> window != null }
            .flatMap { (segment, window) ->
                val windowNonNull = window!!
                val segmentTasks = tasks
                    .filter { it.segmentId == segment.id }
                    .sortedBy { it.orderInSegment }
                if (segmentTasks.isEmpty()) {
                    // 段在 date 生效但无任务，生成空列表（不创建 PENDING）
                    emptyList()
                } else {
                    var cursor = windowNonNull.plannedStart
                    segmentTasks.map { task ->
                        val taskEnd = cursor.plusMinutes(task.plannedDurationMinutes.toLong())
                        val completion = TaskCompletion(
                            id = idGenerator(),
                            taskId = task.id,
                            planId = plan.id,
                            scheduledDate = date,
                            plannedStart = cursor,
                            plannedEnd = taskEnd,
                            status = TaskStatus.PENDING,
                            overrunMinutes = 0,
                            affectedTasks = emptyList(),
                        )
                        cursor = taskEnd
                        completion
                    }
                }
            }
    }
}
