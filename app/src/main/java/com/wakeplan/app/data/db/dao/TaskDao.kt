package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wakeplan.app.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE segment_id = :segmentId ORDER BY order_in_segment ASC")
    fun observeBySegment(segmentId: String): Flow<List<TaskEntity>>

    /**
     * 某计划下所有任务（join time_segments）
     * 用于冲突检测 / 时间线
     */
    @Query(
        """
        SELECT t.* FROM tasks t
        INNER JOIN time_segments s ON t.segment_id = s.id
        WHERE s.plan_id = :planId
        ORDER BY t.order_in_segment ASC
        """
    )
    fun observeByPlan(planId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TaskEntity?

    @Query("SELECT MAX(order_in_segment) FROM tasks WHERE segment_id = :segmentId")
    suspend fun maxOrderInSegment(segmentId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 段内重排 — 事务内按新顺序更新 order_in_segment
     * orderedTaskIds 第一个 → order=0，第二个 → order=1，...
     * 段 id 不一致的 task id 会被跳过
     */
    @Transaction
    suspend fun reorder(segmentId: String, orderedTaskIds: List<String>) {
        orderedTaskIds.forEachIndexed { index, taskId ->
            updateOrder(taskId, index, segmentId)
        }
    }

    @Query("UPDATE tasks SET order_in_segment = :order WHERE id = :taskId AND segment_id = :segmentId")
    suspend fun updateOrder(taskId: String, order: Int, segmentId: String)
}
