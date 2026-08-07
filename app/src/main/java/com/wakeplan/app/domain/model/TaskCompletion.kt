package com.wakeplan.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 任务执行实例 — Task 在某一天的具体记录
 *
 * overrunMinutes = actualEnd - plannedEnd（向下取整，分钟）
 * affectedTasks = 本次自动延期受影响的下游任务 id 列表
 */
data class TaskCompletion(
    val id: String,
    val taskId: String,
    val planId: String,
    val scheduledDate: LocalDate,
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
    val actualStart: LocalDateTime? = null,
    val actualEnd: LocalDateTime? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val overrunMinutes: Int = 0,
    val affectedTasks: List<String> = emptyList(),
)
