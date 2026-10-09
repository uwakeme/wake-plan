package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wakeplan.app.domain.model.FocusOutcome
import java.time.Instant

/**
 * 专注轮记录 — v0.6 番茄钟（schema v5）
 *
 * completion_id **故意不建外键**：task_completions 用 REPLACE 更新（删旧插新），
 * FK CASCADE 会在每次打卡时把关联专注记录级联删掉。plan_id 挂 plans 级联——
 * 计划删除时专注历史随任务完成记录一起清（与 completions 行为一致）。
 */
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("plan_id"),
        Index("started_at"),
    ],
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "completion_id") val completionId: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "actual_minutes") val actualMinutes: Int,
    val outcome: FocusOutcome,
)
