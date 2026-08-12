package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.domain.model.BuiltInTemplates
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模板仓库 — v0.3 简化
 *
 * v0.3 砍段后模板系统要重新设计。commit 1 阶段：
 * - 删 segmentRepository 构造参数
 * - createPlanFromTemplate 只创建空 plan（不创建 tasks）
 * - tasks 复制逻辑等 commit 6 实装
 */
@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val templateDao: PlanTemplateDao,
    private val planRepository: PlanRepository,
) : TemplateRepository {

    override fun observeAll(): Flow<List<PlanTemplate>> =
        templateDao.observeAll().map { list -> list.map { enrichWithSpec(it.toDomain()) } }

    override fun observeById(id: String): Flow<PlanTemplate?> =
        templateDao.observeById(id).map { it?.toDomain()?.let(::enrichWithSpec) }

    override suspend fun getById(id: String): PlanTemplate? =
        templateDao.findById(id)?.toDomain()?.let(::enrichWithSpec)

    /**
     * v0.3 简化：只创建空 plan，tasks 创建留 commit 6 实装
     */
    override suspend fun createPlanFromTemplate(templateId: String, customTitle: String?): Plan {
        val template = getById(templateId)
            ?: error("Template not found: $templateId")
        val planTitle = customTitle ?: template.title
        return planRepository.create(
            title = planTitle,
            colorHex = template.colorHex,
        )
    }

    /**
     * v0.3 简化：enrichWithSpec 直接返回 meta（不做任何展开）
     */
    private fun enrichWithSpec(template: PlanTemplate): PlanTemplate =
        BuiltInTemplates.toPlanTemplate(template)
}
