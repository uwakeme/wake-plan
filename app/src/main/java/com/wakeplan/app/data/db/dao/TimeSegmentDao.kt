package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wakeplan.app.data.db.entity.TimeSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSegmentDao {

    @Query("SELECT * FROM time_segments WHERE plan_id = :planId ORDER BY start_minutes ASC")
    fun observeByPlan(planId: String): Flow<List<TimeSegmentEntity>>

    @Query("SELECT * FROM time_segments ORDER BY start_minutes ASC")
    fun observeAll(): Flow<List<TimeSegmentEntity>>

    @Query("SELECT * FROM time_segments WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<TimeSegmentEntity?>

    @Query("SELECT * FROM time_segments WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TimeSegmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(segment: TimeSegmentEntity)

    @Update
    suspend fun update(segment: TimeSegmentEntity)

    @Delete
    suspend fun delete(segment: TimeSegmentEntity)

    @Query("DELETE FROM time_segments WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 段重叠检测：返回同 planId 内与给定 [startMinutes, endMinutes) 区间有重叠的段
     * 半开区间：a.end == b.start 不算重叠
     * 用于段编辑器的内联提示（PRD AC-02.3）
     */
    @Query(
        """
        SELECT * FROM time_segments
        WHERE plan_id = :planId
          AND id != :excludeId
          AND start_minutes < :endMinutes
          AND end_minutes > :startMinutes
        ORDER BY start_minutes ASC
        """
    )
    suspend fun findOverlapping(
        planId: String,
        startMinutes: Int,
        endMinutes: Int,
        excludeId: String,
    ): List<TimeSegmentEntity>
}
