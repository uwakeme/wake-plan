package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.data.repository.mapper.toEntity
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.repository.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
) : TaskRepository {

    override fun observeBySegment(segmentId: String): Flow<List<Task>> =
        taskDao.observeBySegment(segmentId).map { list -> list.map { it.toDomain() } }

    override fun observeByPlan(planId: String): Flow<List<Task>> =
        taskDao.observeByPlan(planId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Task>> =
        taskDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Task? =
        taskDao.findById(id)?.toDomain()

    override suspend fun create(
        segmentId: String,
        title: String,
        plannedDurationMinutes: Int,
        autoExtend: Boolean,
        isLocked: Boolean,
        note: String?,
    ): Task {
        val nextOrder = (taskDao.maxOrderInSegment(segmentId) ?: -1) + 1
        val task = Task(
            id = newId(),
            segmentId = segmentId,
            title = title,
            plannedDurationMinutes = plannedDurationMinutes,
            autoExtend = autoExtend,
            isLocked = isLocked,
            orderInSegment = nextOrder,
            note = note,
        )
        taskDao.insert(task.toEntity())
        return task
    }

    override suspend fun update(task: Task) {
        taskDao.update(task.toEntity())
    }

    override suspend fun delete(id: String) {
        taskDao.deleteById(id)
    }

    override suspend fun reorder(segmentId: String, orderedTaskIds: List<String>) {
        taskDao.reorder(segmentId, orderedTaskIds)
    }
}
