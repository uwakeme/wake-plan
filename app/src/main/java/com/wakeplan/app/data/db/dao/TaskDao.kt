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

    @Query("SELECT * FROM tasks WHERE plan_id = :planId ORDER BY start_minutes ASC, order_in_plan ASC")
    fun observeByPlan(planId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY start_minutes ASC, order_in_plan ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TaskEntity?

    @Query("SELECT MAX(order_in_plan) FROM tasks WHERE plan_id = :planId")
    suspend fun maxOrderInPlan(planId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 全表清空 — Phase 4.3 备份导入用 */
    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    /**
     * 计划内重排 — 事务内按新顺序更新 order_in_plan
     * orderedTaskIds 第一个 → order=0，第二个 → order=1，...
     * plan_id 不一致的 task id 会被跳过
     */
    @Transaction
    suspend fun reorder(planId: String, orderedTaskIds: List<String>) {
        orderedTaskIds.forEachIndexed { index, taskId ->
            updateOrder(taskId, index, planId)
        }
    }

    @Query("UPDATE tasks SET order_in_plan = :order WHERE id = :taskId AND plan_id = :planId")
    suspend fun updateOrder(taskId: String, order: Int, planId: String)
}
