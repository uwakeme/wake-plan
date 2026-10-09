package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wakeplan.app.data.db.entity.FocusSessionEntity
import java.time.Instant

@Dao
interface FocusSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity)

    /** [start, end) 按开始时间过滤 — 统计用（半开区间，与 completions 口径一致） */
    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE started_at >= :start AND started_at < :end
        ORDER BY started_at ASC
        """
    )
    suspend fun getByStartedAtRange(start: Instant, end: Instant): List<FocusSessionEntity>

    /** 数据保留清理：结束时间早于 cutoff 的行（insert 时已终态定局） */
    @Query("DELETE FROM focus_sessions WHERE ended_at < :cutoff")
    suspend fun deleteEndedBefore(cutoff: Instant): Int
}
