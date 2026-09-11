package com.wakeplan.app.data.repository

import androidx.room.withTransaction
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.domain.model.BuiltInTemplates
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模板仓库 — v0.4 实装完整版
 *
 * - 内置模板 spec 定义在 BuiltInTemplates（代码里），enrichWithSpec 用它补全任务
 * - createPlanFromTemplate 建 plan + 复制全部任务，包在同一个 Room 事务里
 *   （任务 orderInPlan 由 TaskRepository.create 依次 max+1，保持 spec 顺序）
 */
@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val db: WakePlanDatabase,
    private val templateDao: PlanTemplateDao,
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
) : TemplateRepository {

    override fun observeAll(): Flow<List<PlanTemplate>> =
        templateDao.observeAll().map { list -> list.map { enrichWithSpec(it.toDomain()) } }

    override fun observeById(id: String): Flow<PlanTemplate?> =
        templateDao.observeById(id).map { it?.toDomain()?.let(::enrichWithSpec) }

    override suspend fun getById(id: String): PlanTemplate? =
        templateDao.findById(id)?.toDomain()?.let(::enrichWithSpec)

    override suspend fun createPlanFromTemplate(templateId: String, customTitle: String?): Plan {
        val template = getById(templateId)
            ?: error("Template not found: $templateId")
        val planTitle = customTitle ?: template.title

        return db.withTransaction {
            val plan = planRepository.create(
                title = planTitle,
                colorHex = template.colorHex,
            )
            template.tasks.forEach { specTask ->
                taskRepository.create(
                    planId = plan.id,
                    title = specTask.title,
                    startMinutes = specTask.startMinutes,
                    endMinutes = specTask.endMinutes,
                    repeatRule = specTask.repeatRule,
                    customDays = specTask.customDays,
                    isLocked = specTask.isLocked,
                    note = specTask.note,
                )
            }
            plan
        }
    }

    /**
     * 内置模板用完整 spec enrich（带任务预览）；用户模板原样返回
     */
    private fun enrichWithSpec(template: PlanTemplate): PlanTemplate =
        BuiltInTemplates.toPlanTemplate(template)
}
