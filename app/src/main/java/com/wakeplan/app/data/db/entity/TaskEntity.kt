package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wakeplan.app.domain.model.RepeatRule
import java.time.DayOfWeek

/**
 * 任务 — v0.3 扁平化模型
 *
 * v0.3 砍段后任务自带 startMinutes / endMinutes / repeatRule / customDays，
 * 直接挂在 plan 下（外键 plan_id），不再有 segment_id。
 *
 * 跨午夜：endMinutes > 1440（例 25*60=1500 表示 23:00→次日 01:00），
 *   时间线/调度按 plannedStart 落到次日处理。
 *
 * isLocked=true 钉死，链式延期到此停止。
 * autoExtend 字段 v0.3 砍掉（所有任务都参与链式碰撞，"不参与"靠 isLocked 表达）。
 */
@Entity(
    tableName = "tasks",
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
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    val title: String,
    @ColumnInfo(name = "start_minutes") val startMinutes: Int,
    @ColumnInfo(name = "end_minutes") val endMinutes: Int,
    @ColumnInfo(name = "repeat_rule") val repeatRule: RepeatRule,
    @ColumnInfo(name = "custom_days") val customDays: Set<DayOfWeek> = emptySet(),
    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,

    /**
     * 建任务那天的 epochDay（schema v4）。ONCE 任务只在这一天生效；
     * 0 = v3 旧行（迁移默认值），ONCE 退化为每天（兼容旧数据）
     */
    @ColumnInfo(name = "created_epoch_day", defaultValue = "0") val createdEpochDay: Long = 0L,

    @ColumnInfo(name = "order_in_plan") val orderInPlan: Int,
    val note: String? = null,
)
