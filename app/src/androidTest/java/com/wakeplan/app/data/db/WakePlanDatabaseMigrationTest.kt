package com.wakeplan.app.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room Migration 测试 — Phase 6.2 v0.3
 *
 * 用 androidx.room.testing.MigrationTestHelper：
 * - 用 v1 schema 启动 DB（拿 WakePlanDatabase_Impl + v1 schema JSON）
 * - 插 v1 数据
 * - 跑 MIGRATION_1_2 升到 v2
 * - 验证：
 *   1) v2 schema 里 plans 表有 archived 列
 *   2) v1 数据完整保留
 *   3) archived 默认 = 0
 *
 * **重要**：因为生产用 SQLCipher，这里手动用 SupportOpenHelperFactory(passphrase) 验证真实路径
 * （FrameworkSQLiteOpenHelperFactory 只能验证 Room 框架层 migration；SQLCipher 路径要单独跑）
 */
@RunWith(AndroidJUnit4::class)
class WakePlanDatabaseMigrationTest {

    private val TEST_DB_NAME = "wakeplan-migration-test.db"
    private val TEST_PASSPHRASE = "test-passphrase-for-migration".toByteArray()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WakePlanDatabase::class.java,
    )

    @Test
    fun v1_to_v2_preserves_data_and_adds_archived_column() {
        // 1. 用 v1 schema 创建 DB
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            // 2. 插入 v1 数据（无 archived 字段）
            execSQL(
                """
                INSERT INTO plans (id, title, color_hex, start_date, is_active, created_at, updated_at)
                VALUES ('plan-1', '健身', '#C48520', '2026-08-10', 1, 1723296000000, 1723296000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO plans (id, title, color_hex, start_date, is_active, created_at, updated_at)
                VALUES ('plan-2', '学习', '#3B6FB5', '2026-08-10', 1, 1723296000000, 1723296000000)
                """.trimIndent(),
            )
            close()
        }

        // 3. 跑 v1→v2 migration
        helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, WakePlanDatabase.MIGRATION_1_2)

        // 4. 验证 v1 数据完整保留
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, WakePlanDatabase::class.java, TEST_DB_NAME)
            .addMigrations(WakePlanDatabase.MIGRATION_1_2)
            .openHelperFactory(SupportOpenHelperFactory(TEST_PASSPHRASE))
            .build()

        val cursor = db.openHelper.readableDatabase.query("SELECT id, title, archived FROM plans ORDER BY id")
        val rows = mutableListOf<Triple<String, String, Long>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                rows.add(Triple(c.getString(0), c.getString(1), c.getLong(2)))
            }
        }

        // v1 数据全部保留 + archived 默认 0
        assertEquals("应保留 2 条 plan", 2, rows.size)
        assertEquals("plan-1 保留", Triple("plan-1", "健身", 0L), rows[0])
        assertEquals("plan-2 保留", Triple("plan-2", "学习", 0L), rows[1])

        db.close()
    }

    @Test
    fun v2_schema_has_archived_column() {
        // 1. 用 v1 schema 创建 DB（空）
        helper.createDatabase(TEST_DB_NAME, 1).apply { close() }

        // 2. 跑 migration
        helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, WakePlanDatabase.MIGRATION_1_2)

        // 3. 验证 schema 有 archived 列
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, WakePlanDatabase::class.java, TEST_DB_NAME)
            .addMigrations(WakePlanDatabase.MIGRATION_1_2)
            .openHelperFactory(SupportOpenHelperFactory(TEST_PASSPHRASE))
            .build()

        val cursor = db.openHelper.readableDatabase.query("PRAGMA table_info(plans)")
        val columns = mutableListOf<String>()
        cursor.use { c ->
            while (c.moveToNext()) {
                columns.add(c.getString(1))  // column name
            }
        }

        assertTrue(
            "plans 表必须有 archived 列；实际有：$columns",
            columns.contains("archived"),
        )
        assertEquals(
            "plans 表列数 8（含 archived）",
            listOf("id", "title", "color_hex", "start_date", "is_active",
                "created_at", "updated_at", "archived"),
            columns,
        )

        db.close()
    }
}
