package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.PlanTemplate
import kotlinx.coroutines.flow.Flow

/**
 * 模板仓库 — Phase 1.4 实装
 * Phase 5.4.1：observeById（详情预览用）
 * Phase 5.4.2：createPlanFromTemplate（一键新建真 plan）
 */
interface TemplateRepository {

    fun observeAll(): Flow<List<PlanTemplate>>

    fun observeById(id: String): Flow<PlanTemplate?>

    suspend fun getById(id: String): PlanTemplate?

    /**
     * 把模板复制成真 plan + 真 segment + 真 task
     * - 用 [customTitle] 覆盖模板 title；null 时用模板原 title
     * - segment/task 走各自 Repository 的 create() 自动生成新 id
     * - 失败抛异常（没在内部 catch，调用方 ViewModel 用 try/catch 处理）
     */
    suspend fun createPlanFromTemplate(templateId: String, customTitle: String? = null): Plan
}
