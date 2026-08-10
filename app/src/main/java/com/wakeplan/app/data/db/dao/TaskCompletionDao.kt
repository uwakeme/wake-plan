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

    /**
     * 全表 snapshot — Phase 4.1 备份导出专用
     * v0.1 假设用户 < 1k completion；量大时再分批或加日期边界
     */
    @Query("SELECT * FROM task_completions ORDER BY scheduled_date ASC, planned_start ASC")
    suspend fun getAllSnapshot(): List<TaskCompletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: TaskCompletionEntity)

    @Update
    suspend fun update(completion: TaskCompletionEntity)

    /** 全表清空 — Phase 4.3 备份导入用 */
    @Query("DELETE FROM task_completions")
    suspend fun deleteAll()

    /**
     * 日期范围查询 — Phase 5.2 统计用
     * @return [start, end) 半开区间内所有 completion，按 scheduledDate + plannedStart 升序
     */
    @Query(
        """
        SELECT * FROM task_completions
        WHERE scheduled_date >= :start AND scheduled_date < :end
        ORDER BY scheduled_date ASC, planned_start ASC
        """,
    )
    suspend fun getByDateRange(start: LocalDate, end: LocalDate): List<TaskCompletionEntity>
}
