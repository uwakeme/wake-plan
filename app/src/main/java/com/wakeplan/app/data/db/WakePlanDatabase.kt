package com.wakeplan.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wakeplan.app.data.db.converter.Converters
import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.db.dao.TimeSegmentDao
import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity
import com.wakeplan.app.data.db.entity.TimeSegmentEntity

/**
 * Wake Plan Room Database
 * 5 entity 一次声明；DAO 暂空接口，Phase 1+ 实装 CRUD
 *
 * 加密：SQLCipher SupportFactory 注入到 DatabaseModule
 * Schema 导出：`app/schemas/com.wakeplan.app.data.db.WakePlanDatabase/<version>.json`
 *
 * v0.3 schema v2：plans 加 archived 字段
 */
@Database(
    entities = [
        PlanEntity::class,
        TimeSegmentEntity::class,
        TaskEntity::class,
        PlanTemplateEntity::class,
        TaskCompletionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WakePlanDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun timeSegmentDao(): TimeSegmentDao
    abstract fun taskDao(): TaskDao
    abstract fun planTemplateDao(): PlanTemplateDao
    abstract fun taskCompletionDao(): TaskCompletionDao

    companion object {
        const val DB_NAME = "wakeplan.db"

        /**
         * v0.3 Migration 测试基础：v1 → v2
         * plans 表加 archived INTEGER NOT NULL DEFAULT 0
         *
         * 真实数据保留测试见 androidTest/.../WakePlanDatabaseMigrationTest.kt
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE plans ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
