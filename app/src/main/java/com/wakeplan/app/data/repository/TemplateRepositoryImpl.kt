package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.repository.mapper.toDomain
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
        templateDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): PlanTemplate? =
        templateDao.findById(id)?.toDomain()
}
