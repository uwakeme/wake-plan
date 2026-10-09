package com.wakeplan.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wakeplan.app.data.db.converter.Converters
import com.wakeplan.app.data.db.dao.FocusSessionDao
import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.db.entity.FocusSessionEntity
import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity

/**
 * Wake Plan Room Database
 *
 * schema 版本史：
 * - v1（v0.2）：Plan / TimeSegment / Task(挂 segment) / PlanTemplate / TaskCompletion
 * - v2（v0.3）：plans 加 archived（v0.4 归档用）
 * - v3（v0.3.1）：扁平化——砍 time_segments，task 自带起止时间+重复规则，
 *   旧 tasks/task_completions 数据清零（当时走 fallbackToDestructiveMigration）
 * - v4（v0.4.1）：tasks 加 created_epoch_day（ONCE 任务只创建日生效）
 * - v5（v0.6）：focus_sessions 表（番茄钟专注轮）+ task_completions 复合索引
 *   (plan_id, scheduled_date)
 *
 * v0.4.1 起迁移全部显式声明并接入 DatabaseModule；升级缺路径会直接抛错
 * （不再 fallbackToDestructiveMigration 静默清库），新迁移必须配 androidTest。
 */
@Database(
    entities = [
        PlanEntity::class,
        TaskEntity::class,
        PlanTemplateEntity::class,
        TaskCompletionEntity::class,
        FocusSessionEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WakePlanDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun taskDao(): TaskDao
    abstract fun planTemplateDao(): PlanTemplateDao
    abstract fun taskCompletionDao(): TaskCompletionDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        const val DB_NAME = "wakeplan.db"

        /** v1 → v2：plans 加归档标记 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v2 → v3：扁平化任务模型。旧 tasks 挂 segment 维度，砍段后无法映射，
         * 按既定决策清零 tasks / task_completions（plans / templates 保留）。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS time_segments")
                db.execSQL("DROP TABLE IF EXISTS tasks")
                db.execSQL("DELETE FROM task_completions")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tasks (
                        id TEXT NOT NULL PRIMARY KEY,
                        plan_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        start_minutes INTEGER NOT NULL,
                        end_minutes INTEGER NOT NULL,
                        repeat_rule TEXT NOT NULL,
                        custom_days TEXT NOT NULL,
                        is_locked INTEGER NOT NULL,
                        order_in_plan INTEGER NOT NULL,
                        note TEXT,
                        FOREIGN KEY(plan_id) REFERENCES plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_plan_id ON tasks (plan_id)")
            }
        }

        /** v4：tasks 加创建日 epochDay（ONCE 任务只创建日生效；旧行 0 = 兼容旧行为） */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN created_epoch_day INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v5（v0.6）：focus_sessions 表 + task_completions 复合索引。
         * completion_id 不建 FK（completions 走 REPLACE 更新，CASCADE 会误删专注记录）；
         * plan_id 挂 plans 级联。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        completion_id TEXT NOT NULL,
                        plan_id TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        ended_at INTEGER NOT NULL,
                        planned_minutes INTEGER NOT NULL,
                        actual_minutes INTEGER NOT NULL,
                        outcome TEXT NOT NULL,
                        FOREIGN KEY(plan_id) REFERENCES plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_plan_id ON focus_sessions (plan_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_started_at ON focus_sessions (started_at)")
                // 搭车（审查遗留项）：observeByPlanAndDate 高频复合索引
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_completions_plan_id_scheduled_date " +
                        "ON task_completions (plan_id, scheduled_date)"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
