package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanTemplateDao {

    @Query("SELECT * FROM plan_templates ORDER BY is_built_in DESC, title ASC")
    fun observeAll(): Flow<List<PlanTemplateEntity>>

    @Query("SELECT * FROM plan_templates WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PlanTemplateEntity?
}
