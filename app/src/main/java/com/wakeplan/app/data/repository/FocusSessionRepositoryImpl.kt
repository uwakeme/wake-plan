package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.FocusSessionDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.data.repository.mapper.toEntity
import com.wakeplan.app.domain.model.FocusSession
import com.wakeplan.app.domain.repository.FocusSessionRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusSessionRepositoryImpl @Inject constructor(
    private val focusSessionDao: FocusSessionDao,
) : FocusSessionRepository {

    override suspend fun insert(session: FocusSession) {
        focusSessionDao.insert(session.toEntity())
    }

    override suspend fun getByStartedAtRange(start: Instant, end: Instant): List<FocusSession> =
        focusSessionDao.getByStartedAtRange(start, end).map { it.toDomain() }
}
