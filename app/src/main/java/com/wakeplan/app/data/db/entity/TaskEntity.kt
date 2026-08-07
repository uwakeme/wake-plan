package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 段内任务 — 模板定义，运行时由 TaskCompletion 实例化到 scheduledDate
 *
 * autoExtend=true 且 isLocked=false 的任务，所在计划内后续任务会被自动延期
 * isLocked=true 的任务始终钉在原定时点（不参与自动延期）
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TimeSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segment_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("segment_id")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "segment_id") val segmentId: String,
    val title: String,
    @ColumnInfo(name = "planned_duration_minutes") val plannedDurationMinutes: Int,
    @ColumnInfo(name = "auto_extend") val autoExtend: Boolean = true,
    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,
    @ColumnInfo(name = "order_in_segment") val orderInSegment: Int,
    val note: String? = null,
)
