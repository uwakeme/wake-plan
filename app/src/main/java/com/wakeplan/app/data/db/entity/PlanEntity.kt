package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * 计划 — 一个用户可管理多条计划（健身 / 学习 / 副业）
 * 字段定义照搬 PRD 4.2
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
)
