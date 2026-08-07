package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wakeplan.app.data.db.entity.PlanEntity
import kotlinx.coroutines.flow.Flow

/**
 * 计划 DAO
 * id 由调用方生成（UUID），insert 走 OnConflictStrategy.ABORT
 */
@Dao
interface PlanDao {

    @Query("SELECT * FROM plans ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PlanEntity?

    @Query("SELECT COUNT(*) FROM plans")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(plan: PlanEntity)

    @Update
    suspend fun update(plan: PlanEntity)

    @Delete
    suspend fun delete(plan: PlanEntity)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 全表清空 — Phase 4.3 备份导入用 */
    @Query("DELETE FROM plans")
    suspend fun deleteAll()
}
