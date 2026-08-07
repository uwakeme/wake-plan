package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.wakeplan.app.data.db.entity.PlanEntity
import kotlinx.coroutines.flow.Flow

/**
 * 计划 DAO — Phase 0 仅暴露查询接口，CRUD 在 Phase 1 实装
 */
@Dao
interface PlanDao {

    @Query("SELECT * FROM plans ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PlanEntity?

    @Query("SELECT COUNT(*) FROM plans")
    suspend fun count(): Int
}
