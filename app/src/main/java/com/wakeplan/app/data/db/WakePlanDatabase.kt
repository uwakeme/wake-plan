package com.wakeplan.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wakeplan.app.data.db.converter.Converters
import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity

/**
 * Wake Plan Room Database
 *
 * v0.3 schema v3 — 扁平化任务模型（砍 time_segments 表 + 改 tasks 字段）
 *   - 删 time_segments 表
 *   - tasks 表：plan_id / start_minutes / end_minutes / repeat_rule / custom_days
 *     / is_locked / order_in_plan / note；去掉 segment_id / planned_duration_minutes / auto_extend
 *   - alpha 数据清零（fallbackToDestructiveMigration）
 */
@Database(
    entities = [
        PlanEntity::class,
        TaskEntity::class,
        PlanTemplateEntity::class,
        TaskCompletionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WakePlanDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun taskDao(): TaskDao
    abstract fun planTemplateDao(): PlanTemplateDao
    abstract fun taskCompletionDao(): TaskCompletionDao

    companion object {
        const val DB_NAME = "wakeplan.db"
    }
}
