package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.data.repository.mapper.toEntity
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskCompletionRepositoryImpl @Inject constructor(
    private val completionDao: TaskCompletionDao,
) : TaskCompletionRepository {

    override fun observeByDate(date: LocalDate): Flow<List<TaskCompletion>> =
        completionDao.observeByDate(date).map { list -> list.map { it.toDomain() } }

    override fun observeByDateRangeInclusive(start: LocalDate, end: LocalDate): Flow<List<TaskCompletion>> =
        completionDao.observeByDateRangeInclusive(start, end).map { list -> list.map { it.toDomain() } }

    override fun observeByPlanAndDate(planId: String, date: LocalDate): Flow<List<TaskCompletion>> =
        completionDao.observeByPlanAndDate(planId, date).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): TaskCompletion? =
        completionDao.findById(id)?.toDomain()

    override suspend fun upsert(completion: TaskCompletion) {
        completionDao.insert(completion.toEntity()) // OnConflictStrategy.REPLACE
    }

    override suspend fun insertIfAbsent(completion: TaskCompletion) {
        completionDao.insertIgnore(completion.toEntity()) // OnConflictStrategy.IGNORE
    }

    override suspend fun delete(id: String) {
        completionDao.deleteById(id)
    }

    override suspend fun getByDateRange(start: LocalDate, end: LocalDate): List<TaskCompletion> =
        completionDao.getByDateRange(start, end).map { it.toDomain() }

    override suspend fun getAll(): List<TaskCompletion> =
        completionDao.getAllSnapshot().map { it.toDomain() }
}
