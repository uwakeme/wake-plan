package com.wakeplan.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 计划模板 — 5.3 决策：3 个内置 + 用户自存
 * MVP 阶段预留表，Phase 1 接入"另存为模板"和"从模板新建"
 */
@Entity(tableName = "plan_templates")
data class PlanTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int = 1,
)
