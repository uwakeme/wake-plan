package com.wakeplan.app.data.repository.mapper

import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion

/**
 * Entity ↔ Domain Model 单向转换
 * 纯函数，零依赖；测试友好
 */

fun PlanEntity.toDomain(): Plan = Plan(
    id = id,
    title = title,
    colorHex = colorHex,
    startDate = startDate,
    isActive = isActive,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Plan.toEntity(): PlanEntity = PlanEntity(
    id = id,
    title = title,
    colorHex = colorHex,
    startDate = startDate,
    isActive = isActive,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    planId = planId,
    title = title,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    repeatRule = repeatRule,
    customDays = customDays,
    isLocked = isLocked,
    createdEpochDay = createdEpochDay,
    orderInPlan = orderInPlan,
    note = note,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    planId = planId,
    title = title,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    repeatRule = repeatRule,
    customDays = customDays,
    isLocked = isLocked,
    createdEpochDay = createdEpochDay,
    orderInPlan = orderInPlan,
    note = note,
)

fun PlanTemplateEntity.toDomain(): PlanTemplate = PlanTemplate(
    id = id,
    title = title,
    colorHex = colorHex,
    isBuiltIn = isBuiltIn,
)

fun PlanTemplate.toEntity(): PlanTemplateEntity = PlanTemplateEntity(
    id = id,
    title = title,
    colorHex = colorHex,
    isBuiltIn = isBuiltIn,
)

fun TaskCompletionEntity.toDomain(): TaskCompletion = TaskCompletion(
    id = id,
    taskId = taskId,
    planId = planId,
    scheduledDate = scheduledDate,
    plannedStart = plannedStart,
    plannedEnd = plannedEnd,
    actualStart = actualStart,
    actualEnd = actualEnd,
    status = status,
    overrunMinutes = overrunMinutes,
    affectedTasks = affectedTasks,
)

fun TaskCompletion.toEntity(): TaskCompletionEntity = TaskCompletionEntity(
    id = id,
    taskId = taskId,
    planId = planId,
    scheduledDate = scheduledDate,
    plannedStart = plannedStart,
    plannedEnd = plannedEnd,
    actualStart = actualStart,
    actualEnd = actualEnd,
    status = status,
    overrunMinutes = overrunMinutes,
    affectedTasks = affectedTasks,
)

fun com.wakeplan.app.data.db.entity.FocusSessionEntity.toDomain(): com.wakeplan.app.domain.model.FocusSession =
    com.wakeplan.app.domain.model.FocusSession(
        id = id,
        completionId = completionId,
        planId = planId,
        startedAt = startedAt,
        endedAt = endedAt,
        plannedMinutes = plannedMinutes,
        actualMinutes = actualMinutes,
        outcome = outcome,
    )

fun com.wakeplan.app.domain.model.FocusSession.toEntity(): com.wakeplan.app.data.db.entity.FocusSessionEntity =
    com.wakeplan.app.data.db.entity.FocusSessionEntity(
        id = id,
        completionId = completionId,
        planId = planId,
        startedAt = startedAt,
        endedAt = endedAt,
        plannedMinutes = plannedMinutes,
        actualMinutes = actualMinutes,
        outcome = outcome,
    )
