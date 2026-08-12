package com.wakeplan.app.domain.model

/**
 * v0.3 砍段：内置模板的完整 spec 暂不实装（commit 6 整体重写模板系统）
 *
 * 这里只保留 3 个内置模板的元数据（id/title/color），用于 settings 屏的"模板库"列表展示。
 * "应用模板"功能 v0.3 alpha 暂为占位——TemplateRepositoryImpl.createPlanFromTemplate 只创建空 plan。
 */
object BuiltInTemplates {

    /** 拿一个模板的完整 spec（v0.3 暂为 null — 完整 spec 等 commit 6 重新设计） */
    fun specFor(templateId: String): BuiltInTemplateSpec? = null

    /**
     * v0.3 简化：toPlanTemplate 只返回元数据，不 enrich 任何段 / 任务
     */
    fun toPlanTemplate(meta: PlanTemplate): PlanTemplate = meta

    /** 把一个 spec 单独转成 PlanTemplate（spec.id 当 planId）— v0.3 占位 */
    fun toPlanTemplate(spec: BuiltInTemplateSpec): PlanTemplate = PlanTemplate(
        id = spec.id,
        title = spec.title,
        colorHex = spec.colorHex,
        isBuiltIn = true,
    )

    // ============================================================
    // 3 个内置模板的元数据（v0.3 占位 — commit 6 会实装完整 segments + tasks）
    // ============================================================
    val MORNING: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_morning",
        title = "标准晨练",
        colorHex = "#C48520",
    )
    val STUDY: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_study",
        title = "标准学习",
        colorHex = "#3B6FB5",
    )
    val WEEKEND_READING: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_weekend_reading",
        title = "周末阅读",
        colorHex = "#8B5CF6",
    )

    val ALL: List<BuiltInTemplateSpec> = listOf(
        MORNING,
        STUDY,
        WEEKEND_READING,
    )
}

/**
 * v0.3 简化：内置模板 spec 只保留元数据。
 * segments / tasks 字段及对应 TemplateSegmentSpec / TemplateTaskSpec 删掉——commit 6 重新设计。
 */
data class BuiltInTemplateSpec(
    val id: String,
    val title: String,
    val colorHex: String,
)
