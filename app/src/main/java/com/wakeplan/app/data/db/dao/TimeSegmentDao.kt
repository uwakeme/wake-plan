package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.wakeplan.app.data.db.entity.TimeSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSegmentDao {

    @Query("SELECT * FROM time_segments WHERE plan_id = :planId ORDER BY start_minutes ASC")
    fun observeByPlan(planId: String): Flow<List<TimeSegmentEntity>>

    @Query("SELECT * FROM time_segments WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TimeSegmentEntity?
}
