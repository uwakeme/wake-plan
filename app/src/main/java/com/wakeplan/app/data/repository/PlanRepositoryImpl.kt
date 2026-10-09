package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.data.repository.mapper.toEntity
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlanRepository 默认实现
 * 写操作在 Dispatchers.IO（由调用方 ViewModel 提供 coroutineContext，
 *   Hilt 注入时不在这里强制切换，避免双重调度）
 */
@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val planDao: PlanDao,
    private val clock: Clock,
) : PlanRepository {

    override fun observeAll(): Flow<List<Plan>> =
        planDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Plan?> =
        planDao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Plan? =
        planDao.findById(id)?.toDomain()

    override suspend fun create(
        title: String,
        colorHex: String,
        startDate: LocalDate,
    ): Plan {
        val now: Instant = clock.instant()
        val plan = Plan(
            id = newId(),
            title = title,
            colorHex = colorHex,
            startDate = startDate,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        planDao.insert(plan.toEntity())
        return plan
    }

    override suspend fun update(plan: Plan) {
        planDao.update(plan.toEntity().copy(updatedAt = clock.instant()))
    }

    override suspend fun delete(id: String) {
        planDao.deleteById(id)
    }

    override suspend fun count(): Int = planDao.count()
}
