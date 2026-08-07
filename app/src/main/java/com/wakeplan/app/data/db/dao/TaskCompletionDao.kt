package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TaskCompletionDao {

    @Query("SELECT * FROM task_completions WHERE scheduled_date = :date ORDER BY planned_start ASC")
    fun observeByDate(date: LocalDate): Flow<List<TaskCompletionEntity>>

    @Query(
        """
        SELECT * FROM task_completions
        WHERE plan_id = :planId AND scheduled_date = :date
        ORDER BY planned_start ASC
        """
    )
    fun observeByPlanAndDate(planId: String, date: LocalDate): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TaskCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: TaskCompletionEntity)

    @Update
    suspend fun update(completion: TaskCompletionEntity)
}
