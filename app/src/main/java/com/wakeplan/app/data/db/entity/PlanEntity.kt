package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * 计划 — 一个用户可管理多条计划（健身 / 学习 / 副业）
 * 字段定义照搬 PRD 4.2
 *
 * v0.3 加 archived 字段（迁移 v1 → v2）：归档功能 v0.4 实装，本版本只跑 migration
 */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "color_hex") val colorHex: String, // "#RRGGBB"
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    /** v0.3 schema v2 新增：归档标记。false = 正常 / true = 已归档（隐藏） */
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean = false,
)
