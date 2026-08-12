package com.wakeplan.app.domain.usecase

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * 把"task + 日期"展开为 TaskCompletion 列表（PENDING 状态）— v0.3 扁平化
 *
 * v0.3 砍段后，task 自带 startMinutes / endMinutes / repeatRule。
 * 任务的 plannedWindow = TimeWindowCalculator(task, date)；展开逻辑：
 * - 每个 task 在 date 生成一条 TaskCompletion
 * - plannedStart = window.plannedStart, plannedEnd = window.plannedEnd
 * - task 在 date 不生效 → 不生成（如 WEEKDAYS task 在周末）
 *
 * 注意：本 UseCase 是"无状态"展开（输入是纯快照），每次调用结果一致；
 * UI 层通过 TaskCompletionRepository.upsert 去重（OnConflictStrategy.REPLACE）
 */
class TaskCompletionInstantiator @Inject constructor(
    private val timeWindowCalculator: TimeWindowCalculator,
) {

    /**
     * @param plan 仅用于填 planId
     * @param tasks 任务列表（已按 planId 过滤）
     * @param date 要展开的日期
     * @param idGenerator 测试时可注入（默认 UUID.randomUUID）
     */
    operator fun invoke(
        plan: Plan,
        tasks: List<Task>,
        date: LocalDate,
        idGenerator: () -> String = { UUID.randomUUID().toString() },
    ): List<TaskCompletion> {
        return tasks
            .mapNotNull { task -> task to timeWindowCalculator(task, date) }
            .filter { (_, window) -> window != null }
            .map { (task, window) ->
                val windowNonNull = window!!
                TaskCompletion(
                    id = idGenerator(),
                    taskId = task.id,
                    planId = plan.id,
                    scheduledDate = date,
                    plannedStart = windowNonNull.plannedStart,
                    plannedEnd = windowNonNull.plannedEnd,
                    status = TaskStatus.PENDING,
                    overrunMinutes = 0,
                    affectedTasks = emptyList(),
                )
            }
    }
}
