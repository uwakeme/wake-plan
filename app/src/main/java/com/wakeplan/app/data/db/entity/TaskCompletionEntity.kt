package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 任务在某一天的具体实例（同一 Task 在周一和周二是两条记录）
 *
 * overrunMinutes: actualEnd - plannedEnd，向下取整，单位分钟
 * affectedTasks: 本次自动延期受影响的下游任务 id 列表，便于回溯
 */
@Entity(
    tableName = "task_completions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("task_id"),
        Index("plan_id"),
        Index("scheduled_date"),
        Index(value = ["task_id", "scheduled_date"], unique = true),
        // v0.6（schema v5）：observeByPlanAndDate 高频查询的复合索引
        Index(value = ["plan_id", "scheduled_date"]),
    ],
)
data class TaskCompletionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: LocalDate,
    @ColumnInfo(name = "planned_start") val plannedStart: LocalDateTime,
    @ColumnInfo(name = "planned_end") val plannedEnd: LocalDateTime,
    @ColumnInfo(name = "actual_start") val actualStart: LocalDateTime? = null,
    @ColumnInfo(name = "actual_end") val actualEnd: LocalDateTime? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    @ColumnInfo(name = "overrun_minutes") val overrunMinutes: Int = 0,
    @ColumnInfo(name = "affected_tasks") val affectedTasks: List<String> = emptyList(),
)
