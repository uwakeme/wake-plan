package com.wakeplan.app.data.repository.mapper

import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity
import com.wakeplan.app.data.db.entity.TimeSegmentEntity
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TimeSegment

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
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Plan.toEntity(): PlanEntity = PlanEntity(
    id = id,
    title = title,
    colorHex = colorHex,
    startDate = startDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TimeSegmentEntity.toDomain(): TimeSegment = TimeSegment(
    id = id,
    planId = planId,
    title = title,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    repeatRule = repeatRule,
    customDays = customDays,
)

fun TimeSegment.toEntity(): TimeSegmentEntity = TimeSegmentEntity(
    id = id,
    planId = planId,
    title = title,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    repeatRule = repeatRule,
    customDays = customDays,
)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    segmentId = segmentId,
    title = title,
    plannedDurationMinutes = plannedDurationMinutes,
    autoExtend = autoExtend,
    isLocked = isLocked,
    orderInSegment = orderInSegment,
    note = note,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    segmentId = segmentId,
    title = title,
    plannedDurationMinutes = plannedDurationMinutes,
    autoExtend = autoExtend,
    isLocked = isLocked,
    orderInSegment = orderInSegment,
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
