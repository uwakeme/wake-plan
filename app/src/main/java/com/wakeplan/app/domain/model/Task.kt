package com.wakeplan.app.domain.model

/**
 * 任务 Domain Model — 段内的具体可执行项
 *
 * autoExtend=true 且 isLocked=false 的任务，所在计划内后续任务会被自动延期
 * isLocked=true 的任务始终钉在原定时点
 */
data class Task(
    val id: String,
    val segmentId: String,
    val title: String,
    val plannedDurationMinutes: Int,
    val autoExtend: Boolean = true,
    val isLocked: Boolean = false,
    val orderInSegment: Int,
    val note: String? = null,
)
