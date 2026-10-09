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

    /**
     * 闭区间 [start, end] 范围观察 — v0.4.1 跨午夜跨日用：
     * 查 [昨天, 今天] 再在调用方过滤出"属于今天"的（昨天 23:00–今天 00:30 的尾巴）
     */
    @Query(
        """
        SELECT * FROM task_completions
        WHERE scheduled_date >= :start AND scheduled_date <= :end
        ORDER BY scheduled_date ASC, planned_start ASC
        """
    )
    fun observeByDateRangeInclusive(start: LocalDate, end: LocalDate): Flow<List<TaskCompletionEntity>>

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

    /**
     * 仅当行不存在时插入 — 铺当天 PENDING 实例专用。
     * 与 REPLACE 的区别：并发补实例 / 已有真实打卡数据时不会删旧行换新 id
     * （换 id 会让已挂的 AlarmManager PendingIntent 指向不存在的行，提醒静默丢失）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(completion: TaskCompletionEntity)

    @Update
    suspend fun update(completion: TaskCompletionEntity)

    /** 删除单个实例 — 任务改 repeatRule 后清理"不再生效日期"上的未开始实例 */
    @Query("DELETE FROM task_completions WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 全表清空 — Phase 4.3 备份导入用 */
    @Query("DELETE FROM task_completions")
    suspend fun deleteAll()

    /**
     * 数据保留清理（v0.5）：删 scheduled_date 早于 cutoff 且已终态的行。
     * PENDING / IN_PROGRESS 永不删（未开始的实例与正在跑的任务不受保留策略影响）
     */
    @Query(
        """
        DELETE FROM task_completions
        WHERE scheduled_date < :cutoff
            AND status IN ('COMPLETED', 'SKIPPED')
        """
    )
    suspend fun deleteTerminalOlderThan(cutoff: LocalDate): Int

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
