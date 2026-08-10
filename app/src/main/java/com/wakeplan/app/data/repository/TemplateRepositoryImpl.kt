package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.domain.model.BuiltInTemplates
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val templateDao: PlanTemplateDao,
) : TemplateRepository {

    override fun observeAll(): Flow<List<PlanTemplate>> =
        templateDao.observeAll().map { list -> list.map { enrichWithSpec(it.toDomain()) } }

    override fun observeById(id: String): Flow<PlanTemplate?> =
        templateDao.observeById(id).map { it?.toDomain()?.let(::enrichWithSpec) }

    override suspend fun getById(id: String): PlanTemplate? =
        templateDao.findById(id)?.toDomain()?.let(::enrichWithSpec)

    /**
     * Phase 5.4.1: 给 [PlanTemplate] 挂上 segments + tasks
     * - 内置模板从 [BuiltInTemplates] 拿 spec，转成 TimeSegment/Task（占位 id）
     * - 用户模板 v0.2 暂为 empty
     * 5.4.2 一键新建时再用真 id 替换
     */
    private fun enrichWithSpec(template: PlanTemplate): PlanTemplate =
        BuiltInTemplates.toPlanTemplate(template)
}
