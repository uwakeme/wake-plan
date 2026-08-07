package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wakeplan.app.domain.model.RepeatRule
import java.time.DayOfWeek

/**
 * 时间段 — 一天中的一段时间
 * startMinutes / endMinutes 用当天分钟数（0..1440），跨日允许 endMinutes > 1440
 * 跨午夜段由 TimeWindowCalculator 折算实际日期
 *
 * RepeatRule / DayOfWeek 用 domain.model 里的 enum（不再在 entity 包定义）
 */
@Entity(
    tableName = "time_segments",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("plan_id")],
)
data class TimeSegmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    val title: String?,
    @ColumnInfo(name = "start_minutes") val startMinutes: Int,
    @ColumnInfo(name = "end_minutes") val endMinutes: Int,
    @ColumnInfo(name = "repeat_rule") val repeatRule: RepeatRule,
    @ColumnInfo(name = "custom_days") val customDays: Set<DayOfWeek> = emptySet(),
)
