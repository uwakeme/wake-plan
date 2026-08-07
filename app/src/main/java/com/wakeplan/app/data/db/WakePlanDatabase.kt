package com.wakeplan.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
 */
@Database(
    entities = [
        PlanEntity::class,
        TimeSegmentEntity::class,
        TaskEntity::class,
        PlanTemplateEntity::class,
        TaskCompletionEntity::class,
    ],
    version = 1,
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
    }
}
