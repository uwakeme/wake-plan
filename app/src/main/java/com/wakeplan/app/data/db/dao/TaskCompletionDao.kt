package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TaskCompletionDao {

    @Query("SELECT * FROM task_completions WHERE scheduled_date = :date ORDER BY planned_start ASC")
    fun observeByDate(date: LocalDate): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TaskCompletionEntity?
}
