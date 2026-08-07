package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek

/**
 * 重复规则 — 5.3 决策：全量 4 枚举 ONCE / WEEKDAYS / DAILY / CUSTOM
 * CUSTOM 时 customDays 字段必须有值；其他情况 customDays 为空
 */
enum class RepeatRule {
    ONCE,
    WEEKDAYS, // 周一到周五
    DAILY,    // 每天
    CUSTOM,   // 用户自选星期
}

/**
 * 时间段 — 一天中的一段时间
 * startMinutes / endMinutes 用当天分钟数（0..1440），跨日允许 endMinutes > 1440
 * 跨午夜段由 TimeWindowCalculator 折算实际日期
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
