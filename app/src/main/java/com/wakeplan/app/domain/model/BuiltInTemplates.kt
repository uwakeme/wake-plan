package com.wakeplan.app.domain.model

import java.time.DayOfWeek

/**
 * 内置模板的 segments + tasks 定义
 *
 * Phase 5.4.1: 把 3 个内置模板的段 + 任务 hardcode 在这里
 * - segments/tasks 不进 DB（plan_templates 表只存元数据）
 * - 5.4.2 一键新建时 [com.wakeplan.app.data.repository.TemplateRepositoryImpl] 会把这里的数据
 *   复制成真 plan + 真 segment + 真 task（生成新 id，关联 planId/segmentId）
 *
 * id 规则：
 * - segmentId = "${tplId}_seg_${idx}"（按 segments 列表顺序）
 * - taskId = "${tplId}_task_${segmentIdx}_${taskIdx}"
 * - task.segmentId 引用上面的 segmentId
 *
 * 时长校验：每个段的 task 总时长 <= 段 duration（留缓冲）
 */
object BuiltInTemplates {

    /** 拿一个模板的完整定义（segments + tasks）；null 表示未知 id */
    fun specFor(templateId: String): BuiltInTemplateSpec? =
        ALL_BY_ID[templateId]

    /**
     * 把 spec 合并到 PlanTemplate 元数据上（供 UI 预览用）— 把 TemplateSegmentSpec 转成 TimeSegment 占位
     * 用户自定义模板（无 spec）保持 segments/tasks = empty
     */
    fun toPlanTemplate(meta: PlanTemplate): PlanTemplate {
        val spec = specFor(meta.id) ?: return meta
        return expandSpecIntoPlanTemplate(spec, basePlanId = meta.id, overrideTitle = meta.title, overrideColor = meta.colorHex, isBuiltIn = meta.isBuiltIn)
    }

    /** 把一个 spec 单独转成 PlanTemplate（spec.id 当 planId）— 用于 MORNING/STUDY/WEEKEND_READING 常量初始化 */
    fun toPlanTemplate(spec: BuiltInTemplateSpec): PlanTemplate = expandSpecIntoPlanTemplate(
        spec, basePlanId = spec.id, overrideTitle = spec.title, overrideColor = spec.colorHex, isBuiltIn = true,
    )

    /**
     * 共享展开逻辑：spec.segments → List<TimeSegment>，spec.tasks → List<Task>
     * 用 basePlanId 作为 segmentId / taskId 前缀（保持可追溯到来源模板）
     */
    private fun expandSpecIntoPlanTemplate(
        spec: BuiltInTemplateSpec,
        basePlanId: String,
        overrideTitle: String,
        overrideColor: String,
        isBuiltIn: Boolean,
    ): PlanTemplate {
        val segmentIdMap = spec.segments.indices.map { idx -> "${basePlanId}_seg_$idx" }
        val segmentDomains = spec.segments.mapIndexed { idx, seg ->
            TimeSegment(
                id = segmentIdMap[idx],
                planId = basePlanId,
                title = seg.title,
                startMinutes = seg.startMinutes,
                endMinutes = seg.endMinutes,
                repeatRule = seg.repeatRule,
                customDays = seg.customDays,
            )
        }
        val taskDomains = spec.tasks.mapIndexed { taskIdx, taskSpec ->
            Task(
                id = "${basePlanId}_task_${taskSpec.segmentIndex}_$taskIdx",
                segmentId = segmentIdMap[taskSpec.segmentIndex],
                title = taskSpec.title,
                plannedDurationMinutes = taskSpec.plannedDurationMinutes,
                autoExtend = taskSpec.autoExtend,
                isLocked = taskSpec.isLocked,
                orderInSegment = taskIdx,
                note = taskSpec.note,
            )
        }
        return PlanTemplate(
            id = basePlanId,
            title = overrideTitle,
            colorHex = overrideColor,
            isBuiltIn = isBuiltIn,
            segments = segmentDomains,
            tasks = taskDomains,
        )
    }

    /** 内部缓存：ALL 的 by-id 索引；懒加载避免初始化顺序问题 */
    private val ALL_BY_ID: Map<String, BuiltInTemplateSpec> by lazy {
        ALL.associateBy { it.id }
    }

    // ============================================================
    // 1. 标准晨练 — 06:00-07:00 DAILY，热身 10 + 跑步 30 + 拉伸 10 = 50min (60min 段)
    // ============================================================
    val MORNING: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_morning",
        title = "标准晨练",
        colorHex = "#C48520",
        segments = listOf(
            TemplateSegmentSpec(
                title = "晨练段",
                startMinutes = 6 * 60,     // 06:00
                endMinutes = 7 * 60,       // 07:00
                repeatRule = RepeatRule.DAILY,
                customDays = emptySet(),
            ),
        ),
        tasks = listOf(
            TemplateTaskSpec(segmentIndex = 0, title = "热身", plannedDurationMinutes = 10, autoExtend = true, isLocked = false, note = "动态拉伸 + 关节活动"),
            TemplateTaskSpec(segmentIndex = 0, title = "跑步", plannedDurationMinutes = 30, autoExtend = true, isLocked = false, note = null),
            TemplateTaskSpec(segmentIndex = 0, title = "拉伸", plannedDurationMinutes = 10, autoExtend = true, isLocked = false, note = null),
        ),
    )

    // ============================================================
    // 2. 标准学习 — 09:30-10:30 DAILY，阅读 40 + 复习 20 = 60min
    // ============================================================
    val STUDY: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_study",
        title = "标准学习",
        colorHex = "#3B6FB5",
        segments = listOf(
            TemplateSegmentSpec(
                title = "早读段",
                startMinutes = 9 * 60 + 30, // 09:30
                endMinutes = 10 * 60 + 30,  // 10:30
                repeatRule = RepeatRule.DAILY,
                customDays = emptySet(),
            ),
        ),
        tasks = listOf(
            TemplateTaskSpec(segmentIndex = 0, title = "阅读", plannedDurationMinutes = 40, autoExtend = true, isLocked = false, note = null),
            TemplateTaskSpec(segmentIndex = 0, title = "复习", plannedDurationMinutes = 20, autoExtend = true, isLocked = false, note = null),
        ),
    )

    // ============================================================
    // 3. 周末阅读 — 10:00-12:00 CUSTOM {SAT, SUN}，精读 60 + 整理 30 + 杂项 30 = 120min
    // ============================================================
    val WEEKEND_READING: BuiltInTemplateSpec = BuiltInTemplateSpec(
        id = "tpl_weekend_reading",
        title = "周末阅读",
        colorHex = "#8B5CF6",
        segments = listOf(
            TemplateSegmentSpec(
                title = "周末阅读段",
                startMinutes = 10 * 60,    // 10:00
                endMinutes = 12 * 60,      // 12:00
                repeatRule = RepeatRule.CUSTOM,
                customDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ),
        ),
        tasks = listOf(
            TemplateTaskSpec(segmentIndex = 0, title = "精读", plannedDurationMinutes = 60, autoExtend = true, isLocked = false, note = "深度阅读一本书"),
            TemplateTaskSpec(segmentIndex = 0, title = "整理笔记", plannedDurationMinutes = 30, autoExtend = true, isLocked = false, note = null),
            TemplateTaskSpec(segmentIndex = 0, title = "杂项", plannedDurationMinutes = 30, autoExtend = true, isLocked = false, note = "微信读书 / RSS / 公众号"),
        ),
    )

    val ALL: List<BuiltInTemplateSpec> = listOf(
        MORNING,
        STUDY,
        WEEKEND_READING,
    )
}

/** 一个内置模板的完整 spec（段 + 任务） */
data class BuiltInTemplateSpec(
    val id: String,
    val title: String,
    val colorHex: String,
    val segments: List<TemplateSegmentSpec>,
    val tasks: List<TemplateTaskSpec>,
)

/** 模板内段的 spec — 跟 TimeSegment 一一对应（除 planId 之外） */
data class TemplateSegmentSpec(
    val title: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val repeatRule: RepeatRule,
    val customDays: Set<DayOfWeek> = emptySet(),
) {
    init {
        require(endMinutes > startMinutes) { "endMinutes ($endMinutes) 必须 > startMinutes ($startMinutes)" }
        require(startMinutes in 0..(24 * 60)) { "startMinutes 超出 0..1440: $startMinutes" }
        require(endMinutes in 1..(48 * 60)) { "endMinutes 超出 1..2880: $endMinutes（跨午夜允许到 48:00）" }
    }
}

/** 模板内任务的 spec — 跟 Task 一一对应（除 segmentId 之外） */
data class TemplateTaskSpec(
    val segmentIndex: Int,  // 引用 segments[index]
    val title: String,
    val plannedDurationMinutes: Int,
    val autoExtend: Boolean = true,
    val isLocked: Boolean = false,
    val note: String? = null,
) {
    init {
        require(plannedDurationMinutes > 0) { "时长必须 > 0: $plannedDurationMinutes" }
        require(segmentIndex >= 0) { "segmentIndex 必须 >= 0: $segmentIndex" }
    }
}
