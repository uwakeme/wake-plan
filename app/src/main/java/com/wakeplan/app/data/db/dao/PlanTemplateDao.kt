package com.wakeplan.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanTemplateDao {

    @Query("SELECT * FROM plan_templates ORDER BY is_built_in DESC, title ASC")
    fun observeAll(): Flow<List<PlanTemplateEntity>>

    @Query("SELECT * FROM plan_templates WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PlanTemplateEntity?>

    @Query("SELECT * FROM plan_templates WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PlanTemplateEntity?

    @Query("SELECT COUNT(*) FROM plan_templates")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: PlanTemplateEntity)

    @Update
    suspend fun update(template: PlanTemplateEntity)

    @Delete
    suspend fun delete(template: PlanTemplateEntity)
}
