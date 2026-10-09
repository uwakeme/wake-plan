package com.wakeplan.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room Migration 测试 — v0.4.1 重写
 *
 * 覆盖全链路 v1 → v4：
 * - MIGRATION_1_2：plans 加 archived
 * - MIGRATION_2_3：砍 time_segments、重建扁平 tasks、清 task_completions（既定决策）
 * - MIGRATION_3_4：tasks 加 created_epoch_day（ONCE 只创建日生效）
 *
 * schema JSON 通过 androidTest assets（build.gradle.kts sourceSets 挂 $projectDir/schemas）
 * 供 MigrationTestHelper 读取；runMigrationsAndValidate 返回的 db 直接断言，
 * 不做二次 open（避免明文/密文 factory 混用）。
 *
 * 背景：v0.3.1 删除 MIGRATION_1_2 后本文件引用悬空符号无法编译；v0.4.1 恢复
 * 1→2 迁移并补齐 2→3、3→4，DatabaseModule 接 addMigrations、拆 destructive fallback。
 */
@RunWith(AndroidJUnit4::class)
class WakePlanDatabaseMigrationTest {

    private val TEST_DB_NAME = "wakeplan-migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WakePlanDatabase::class.java,
    )

    @Test
    fun migrate_1_to_4_preserves_plans_rebuilds_flat_tasks() {
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO plans (id, title, color_hex, start_date, is_active, created_at, updated_at)
                VALUES ('plan-1', '健身', '#C48520', '2026-08-10', 1, 1723296000000, 1723296000000)
                """.trimIndent(),
            )
            // v1 三层模型数据：segment + 挂 segment 的 task + completion
            execSQL(
                """
                INSERT INTO time_segments (id, plan_id, title, start_minutes, end_minutes, repeat_rule, custom_days)
                VALUES ('seg-1', 'plan-1', '早晨', 360, 420, 'DAILY', '')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO tasks (id, segment_id, title, planned_duration_minutes, auto_extend, is_locked, order_in_segment)
                VALUES ('task-1', 'seg-1', '晨跑', 30, 1, 0, 0)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 4, true,
            WakePlanDatabase.MIGRATION_1_2,
            WakePlanDatabase.MIGRATION_2_3,
            WakePlanDatabase.MIGRATION_3_4,
        )

        // plans 全保留 + archived 默认 0
        db.query("SELECT id, title, archived FROM plans").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("plan-1", c.getString(0))
            assertEquals("健身", c.getString(1))
            assertEquals(0L, c.getLong(2))
            assertFalse(c.moveToNext())
        }
        // 扁平化决策：旧 tasks（segment 维度）清空、time_segments 消失、completion 清空
        db.query("SELECT COUNT(*) FROM tasks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM task_completions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='time_segments'",
        ).use { c ->
            assertFalse("time_segments 表应已删除", c.moveToFirst())
        }
    }

    @Test
    fun migrate_3_to_4_adds_created_epoch_day_defaulting_zero() {
        helper.createDatabase(TEST_DB_NAME + "-v3", 3).apply {
            execSQL(
                """
                INSERT INTO plans (id, title, color_hex, start_date, is_active, created_at, updated_at, archived)
                VALUES ('plan-9', '学习', '#3B6FB5', '2026-09-01', 1, 1725148800000, 1725148800000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO tasks (id, plan_id, title, start_minutes, end_minutes, repeat_rule, custom_days, is_locked, order_in_plan)
                VALUES ('task-9', 'plan-9', '阅读', 540, 600, 'ONCE', '', 0, 0)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_NAME + "-v3", 4, true,
            WakePlanDatabase.MIGRATION_3_4,
        )

        // 旧行 created_epoch_day 默认 0（ONCE 兼容旧行为：每天生效）
        db.query("SELECT id, created_epoch_day FROM tasks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("task-9", c.getString(0))
            assertEquals(0L, c.getLong(1))
        }
    }
}
