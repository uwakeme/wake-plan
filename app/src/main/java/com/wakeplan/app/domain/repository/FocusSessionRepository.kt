package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.FocusSession
import java.time.Instant

/**
 * 专注轮记录仓库 — v0.6 番茄钟
 */
interface FocusSessionRepository {

    suspend fun insert(session: FocusSession)

    /** [start, end) 按开始时间 — 统计用 */
    suspend fun getByStartedAtRange(start: Instant, end: Instant): List<FocusSession>
}
