package com.wakeplan.app.data.backup

import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity

/**
 * 备份 DTO ↔ Room entity 双向映射 — Phase 4.1
 *
 * 字段一一对应，DTO 不带 Room 注解便于序列化
 *
 * v0.3 砍段：TimeSegmentEntity 删了，对应 toDto()/toEntity() 一起删
 */
fun PlanEntity.toDto() = BackupPlan(
    id = id,
    title = title,
    colorHex = colorHex,
    startDate = startDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BackupPlan.toEntity() = PlanEntity(
    id = id,
    title = title,
    colorHex = colorHex,
    startDate = startDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TaskEntity.toDto() = BackupTask(
    id = id,
    planId = planId,
    title = title,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    repeatRule = repeatRule,
    customDays = customDays,
    isLocked = isLocked,
    orderInPlan = orderInPlan,
    note = note,
)

fun BackupTask.toEntity() = TaskEntity(
    id = id,
    planId = planId,
    title = title,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    repeatRule = repeatRule,
    customDays = customDays,
    isLocked = isLocked,
    orderInPlan = orderInPlan,
    note = note,
)

fun TaskCompletionEntity.toDto() = BackupCompletion(
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

fun BackupCompletion.toEntity() = TaskCompletionEntity(
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

fun PlanTemplateEntity.toDto() = BackupTemplate(
    id = id,
    title = title,
    colorHex = colorHex,
    isBuiltIn = isBuiltIn,
    schemaVersion = schemaVersion,
)

fun BackupTemplate.toEntity() = PlanTemplateEntity(
    id = id,
    title = title,
    colorHex = colorHex,
    isBuiltIn = isBuiltIn,
    schemaVersion = schemaVersion,
)
