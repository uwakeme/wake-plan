package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.PlanTemplate
import kotlinx.coroutines.flow.Flow

/**
 * 模板仓库 — Phase 1.4 实装
 * Phase 5.4.1：observeById（详情预览用）
 */
interface TemplateRepository {

    fun observeAll(): Flow<List<PlanTemplate>>

    fun observeById(id: String): Flow<PlanTemplate?>

    suspend fun getById(id: String): PlanTemplate?
}
