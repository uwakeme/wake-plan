package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.PlanTemplate
import kotlinx.coroutines.flow.Flow

/**
 * 模板仓库 — Phase 1.4 才实装
 * Phase 1.1 仅暴露接口，UI 不调用
 */
interface TemplateRepository {

    fun observeAll(): Flow<List<PlanTemplate>>

    suspend fun getById(id: String): PlanTemplate?
}
