package com.wakeplan.app.domain.model

import java.time.DayOfWeek

/**
 * 内置模板 — v0.4 实装完整 spec（v0.3 砍段后重设计落地）
 *
 * spec 定义在代码里（不走 DB），apply 时经 createPlanFromTemplate 复制成
 * 真 plan + 真 task（id 由 Repository 生成）。
 */
object BuiltInTemplates {

    /** 拿一个内置模板的完整 spec；非内置 id 返回 null */
    fun specFor(templateId: String): BuiltInTemplateSpec? =
        ALL.firstOrNull { it.id == templateId }

    /**
     * DB 元数据 → PlanTemplate：内置的用完整 spec enrich（带任务），用户模板原样返回
     */
    fun toPlanTemplate(meta: PlanTemplate): PlanTemplate =
        if (meta.isBuiltIn) specFor(meta.id)?.let(::toPlanTemplate) ?: meta else meta

    /** 把一个 spec 转成 PlanTemplate（tasks 带占位 id，仅预览用；apply 时生成新 id） */
    fun toPlanTemplate(spec: BuiltInTemplateSpec): PlanTemplate = PlanTemplate(
        id = spec.id,
        title = spec.title,
        colorHex = spec.colorHex,
        isBuiltIn = true,
        tasks = spec.tasks.mapIndexed { index, s ->
            Task(
                id = "${spec.id}-task-$index",
                planId = spec.id,
                title = s.title,
                startMinutes = s.startMinutes,
                endMinutes = s.endMinutes,
                repeatRule = s.repeatRule,
                customDays = s.customDays,
                isLocked = s.isLocked,
                orderInPlan = index,
                note = s.note,
            )
        },
    )

    // ============================================================
    // 3 个内置模板 spec
    // ============================================================

    /** 标准晨练：起床到出门的固定晨间流程，跑步段钉死（不允许被前面任务顺延挤掉） */
    val MORNING: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_morning",
        title = "标准晨练",
        colorHex = "#C48520",
        tasks = listOf(
            TemplateTaskSpec(title = "起床 / 喝水", startMinutes = 6 * 60, endMinutes = 6 * 60 + 10),
            TemplateTaskSpec(title = "拉伸热身", startMinutes = 6 * 60 + 10, endMinutes = 6 * 60 + 25),
            TemplateTaskSpec(
                title = "跑步 40 分钟",
                startMinutes = 6 * 60 + 25,
                endMinutes = 7 * 60 + 5,
                isLocked = true,
                note = "钉死：超时也不顺延，宁可少跑",
            ),
            TemplateTaskSpec(title = "冲澡整理", startMinutes = 7 * 60 + 5, endMinutes = 7 * 60 + 30),
            TemplateTaskSpec(title = "早餐", startMinutes = 7 * 60 + 30, endMinutes = 8 * 60),
        ),
    )

    /** 标准学习：两个深度学习块 + 复盘，工作日晚间加一轮复习 */
    val STUDY: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_study",
        title = "标准学习",
        colorHex = "#3B6FB5",
        tasks = listOf(
            TemplateTaskSpec(title = "晨读 / 背诵", startMinutes = 7 * 60, endMinutes = 7 * 60 + 40),
            TemplateTaskSpec(title = "深度学习块 1", startMinutes = 8 * 60, endMinutes = 10 * 60),
            TemplateTaskSpec(title = "复盘笔记", startMinutes = 10 * 60, endMinutes = 10 * 60 + 20),
            TemplateTaskSpec(title = "深度学习块 2", startMinutes = 10 * 60 + 30, endMinutes = 12 * 60),
            TemplateTaskSpec(
                title = "晚间复习",
                startMinutes = 20 * 60 + 30,
                endMinutes = 21 * 60 + 30,
                repeatRule = RepeatRule.WEEKDAYS,
            ),
        ),
    )

    /** 周末阅读：只在周末生效的半日阅读安排 */
    val WEEKEND_READING: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_weekend_reading",
        title = "周末阅读",
        colorHex = "#8B5CF6",
        tasks = listOf(
            TemplateTaskSpec(
                title = "上午阅读",
                startMinutes = 9 * 60 + 30,
                endMinutes = 11 * 60 + 30,
                repeatRule = RepeatRule.CUSTOM,
                customDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ),
            TemplateTaskSpec(
                title = "午后小憩",
                startMinutes = 13 * 60,
                endMinutes = 13 * 60 + 30,
                repeatRule = RepeatRule.CUSTOM,
                customDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ),
            TemplateTaskSpec(
                title = "下午阅读 + 笔记",
                startMinutes = 14 * 60,
                endMinutes = 16 * 60,
                repeatRule = RepeatRule.CUSTOM,
                customDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ),
            TemplateTaskSpec(
                title = "输出 / 读书笔记整理",
                startMinutes = 16 * 60 + 30,
                endMinutes = 17 * 60 + 30,
                repeatRule = RepeatRule.CUSTOM,
                customDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ),
        ),
    )

    val ALL: List<BuiltInTemplateSpec> = listOf(
        MORNING,
        STUDY,
        WEEKEND_READING,
    )
}

/**
 * 内置模板任务 spec — v0.4
 *
 * apply 时字段直接透传 TaskRepository.create()，新 id / orderInPlan 由仓库生成。
 */
data class TemplateTaskSpec(
    val title: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val repeatRule: RepeatRule = RepeatRule.DAILY,
    val customDays: Set<DayOfWeek> = emptySet(),
    val isLocked: Boolean = false,
    val note: String? = null,
)

/**
 * v0.4：内置模板 spec 带完整任务列表
 */
data class BuiltInTemplateSpec(
    val id: String,
    val title: String,
    val colorHex: String,
    val tasks: List<TemplateTaskSpec> = emptyList(),
)
