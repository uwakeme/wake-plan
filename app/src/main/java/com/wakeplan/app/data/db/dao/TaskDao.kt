package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.wakeplan.app.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE segment_id = :segmentId ORDER BY order_in_segment ASC")
    fun observeBySegment(segmentId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TaskEntity?
}
