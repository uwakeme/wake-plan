package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.TimeSegmentDao
import com.wakeplan.app.data.db.entity.TimeSegmentEntity
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.data.repository.mapper.toEntity
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TimeSegment
import com.wakeplan.app.domain.repository.SegmentRepository
import com.wakeplan.app.domain.repository.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SegmentRepositoryImpl @Inject constructor(
    private val segmentDao: TimeSegmentDao,
) : SegmentRepository {

    override fun observeByPlan(planId: String): Flow<List<TimeSegment>> =
        segmentDao.observeByPlan(planId).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): TimeSegment? =
        segmentDao.findById(id)?.toDomain()

    override suspend fun create(
        planId: String,
        title: String?,
        startMinutes: Int,
        endMinutes: Int,
        repeatRule: RepeatRule,
        customDays: Set<DayOfWeek>,
    ): TimeSegment {
        val segment = TimeSegment(
            id = newId(),
            planId = planId,
            title = title,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            repeatRule = repeatRule,
            customDays = if (repeatRule == RepeatRule.CUSTOM) customDays else emptySet(),
        )
        segmentDao.insert(segment.toEntity())
        return segment
    }

    override suspend fun update(segment: TimeSegment) {
        segmentDao.update(segment.toEntity())
    }

    override suspend fun delete(id: String) {
        segmentDao.deleteById(id)
    }

    /** 段重叠检测（暴露给 ViewModel 用） */
    suspend fun findOverlapping(
        planId: String,
        startMinutes: Int,
        endMinutes: Int,
        excludeId: String,
    ): List<TimeSegment> =
        segmentDao.findOverlapping(planId, startMinutes, endMinutes, excludeId)
            .map(TimeSegmentEntity::toDomain)
}
