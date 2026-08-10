package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.domain.model.BuiltInTemplates
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.SegmentRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val templateDao: PlanTemplateDao,
    private val planRepository: PlanRepository,
    private val segmentRepository: SegmentRepository,
    private val taskRepository: TaskRepository,
) : TemplateRepository {

    override fun observeAll(): Flow<List<PlanTemplate>> =
        templateDao.observeAll().map { list -> list.map { enrichWithSpec(it.toDomain()) } }

    override fun observeById(id: String): Flow<PlanTemplate?> =
        templateDao.observeById(id).map { it?.toDomain()?.let(::enrichWithSpec) }

    override suspend fun getById(id: String): PlanTemplate? =
        templateDao.findById(id)?.toDomain()?.let(::enrichWithSpec)

    /**
     * Phase 5.4.2: 一键新建 plan
     * 流程：
     * 1. 拉 PlanTemplate（含 segments + tasks 的占位 id）
     * 2. planRepository.create(planTitle, planColor) → 新 plan + 真 id
     * 3. 依次创真 segment，记录 placeholder → 真 id 映射
     * 4. 依次创真 task，把 task.segmentId 替换为真 segmentId
     * 5. 返回新 plan
     *
     * 用户自定义模板（segments/tasks 为空）只创建空 plan
     */
    override suspend fun createPlanFromTemplate(templateId: String, customTitle: String?): Plan {
        val template = getById(templateId)
            ?: error("Template not found: $templateId")
        val planTitle = customTitle ?: template.title
        val plan = planRepository.create(
            title = planTitle,
            colorHex = template.colorHex,
        )
        if (template.segments.isEmpty()) return plan

        // 1. 真 segment + 记录 placeholder → 真 id 映射
        val newSegments = template.segments.map { seg ->
            segmentRepository.create(
                planId = plan.id,
                title = seg.title,
                startMinutes = seg.startMinutes,
                endMinutes = seg.endMinutes,
                repeatRule = seg.repeatRule,
                customDays = seg.customDays,
            )
        }
        val placeholderToNewSegmentId: Map<String, String> =
            template.segments.indices.associate { idx ->
                template.segments[idx].id to newSegments[idx].id
            }

        // 2. 真 task（顺序创建 → orderInSegment 自然递增 0..N）
        template.tasks.forEach { task ->
            val newSegmentId = placeholderToNewSegmentId[task.segmentId]
                ?: error("Template task ${task.id} 引用了未知 segmentId ${task.segmentId}")
            taskRepository.create(
                segmentId = newSegmentId,
                title = task.title,
                plannedDurationMinutes = task.plannedDurationMinutes,
                autoExtend = task.autoExtend,
                isLocked = task.isLocked,
                note = task.note,
            )
        }
        return plan
    }

    /**
     * Phase 5.4.1: 给 [PlanTemplate] 挂上 segments + tasks
     * - 内置模板从 [BuiltInTemplates] 拿 spec，转成 TimeSegment/Task（占位 id）
     * - 用户模板 v0.2 暂为 empty
     * 5.4.2 一键新建时再用真 id 替换
     */
    private fun enrichWithSpec(template: PlanTemplate): PlanTemplate =
        BuiltInTemplates.toPlanTemplate(template)
}
