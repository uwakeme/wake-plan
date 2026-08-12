package com.wakeplan.app.domain.model

/**
 * 计划模板 Domain Model — v0.3 简化
 *
 * v0.3 砍段后，模板结构要重新设计：任务自带 startMinutes / endMinutes / repeatRule，
 * 不再有"段 + 段内任务"的嵌套。完整模板系统（含"另存为模板"）留 commit 6 整体重写。
 *
 * v0.3 临时简化：
 * - 删 segments 字段
 * - tasks 字段保留（task 域模型已重定义）
 * - enrichWithSpec / createPlanFromTemplate 只创建空 plan
 */
data class PlanTemplate(
    val id: String,
    val title: String,
    val colorHex: String,
    val isBuiltIn: Boolean = false,
    val tasks: List<Task> = emptyList(),
)
