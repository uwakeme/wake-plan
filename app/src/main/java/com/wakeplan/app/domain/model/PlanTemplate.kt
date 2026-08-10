package com.wakeplan.app.domain.model

/**
 * 计划模板 Domain Model — 5.3 决策：3 个内置 + 用户自存
 *
 * Phase 5.4.1：扩展为"模板即迷你计划" — 含 segments + tasks（用于预览 + 5.4.2 一键新建）
 * - 内置模板的 segments/tasks 在 [BuiltInTemplates] hardcode（pure data，不进 DB）
 * - 用户自存模板的 segments/tasks v0.2 仍为空（v0.3 再加"另存为模板"）
 * - id 字段：entity 里就是 DB rowid；merge 时用同一个 id 作为前缀
 *
 * segments 内的 TimeSegment.id / tasks 内的 Task.segmentId 都是 placeholder（"tpl_xxx_seg_0"），
 * 应用模板生成真 plan 时由 [TemplateRepository.createPlanFromTemplate] 重新生成
 */
data class PlanTemplate(
    val id: String,
    val title: String,
    val colorHex: String,
    val isBuiltIn: Boolean = false,
    val segments: List<TimeSegment> = emptyList(),
    val tasks: List<Task> = emptyList(),
)
