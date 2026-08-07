package com.wakeplan.app.domain.model

/**
 * 计划模板 Domain Model — 5.3 决策：3 个内置 + 用户自存
 * MVP 阶段只存元数据；模板内的段 + 任务在 Phase 1.4 单独建模
 */
data class PlanTemplate(
    val id: String,
    val title: String,
    val colorHex: String,
    val isBuiltIn: Boolean = false,
)
