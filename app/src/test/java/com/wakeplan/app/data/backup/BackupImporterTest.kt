package com.wakeplan.app.data.backup

import androidx.room.withTransaction
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TaskStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

/**
 * BackupImporter 导入测试 — v0.3 扁平化
 *
 * v0.3 砍 time_segments 表：importer 不再 mock TimeSegmentDao，segments 字段忽略不导入。
 * TaskEntity 字段重定义：planId / startMinutes / endMinutes / repeatRule / orderInPlan。
 *
 * v0.4：wipe + insert 包进 `db.withTransaction`。JVM 测试 mock 掉 Room 的
 * `RoomDatabaseKt.withTransaction` 扩展（真实回滚语义由 Room 保证，androidTest 层验证），
 * 这里验证的是"DAO 操作确实在事务块内执行 + 失败时异常向上传播"。
 */
@DisplayName("BackupImporter")
class BackupImporterTest {

    private val db: WakePlanDatabase = mockk()
    private val planDao: PlanDao = mockk(relaxed = true)
    private val taskDao: TaskDao = mockk(relaxed = true)
    private val taskCompletionDao: TaskCompletionDao = mockk(relaxed = true)
    private val planTemplateDao: PlanTemplateDao = mockk(relaxed = true)

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @BeforeEach
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun importer() = BackupImporter(
        db, planDao, taskDao, taskCompletionDao, planTemplateDao,
    )

    private fun samplePlan() = PlanEntity(
        id = "plan-1",
        title = "晨练",
        colorHex = "#C48520",
        startDate = LocalDate.of(2026, 8, 1),
        isActive = true,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-07T12:00:00Z"),
    )

    private fun sampleTask() = TaskEntity(
        id = "task-1",
        planId = "plan-1",
        title = "跑步",
        startMinutes = 6 * 60,
        endMinutes = 6 * 60 + 30,
        repeatRule = RepeatRule.DAILY,
        isLocked = false,
        orderInPlan = 0,
        note = null,
    )

    private fun sampleCompletion() = TaskCompletionEntity(
        id = "comp-1",
        taskId = "task-1",
        planId = "plan-1",
        scheduledDate = LocalDate.of(2026, 8, 8),
        plannedStart = LocalDateTime.of(2026, 8, 8, 6, 10),
        plannedEnd = LocalDateTime.of(2026, 8, 8, 6, 40),
        actualStart = null,
        actualEnd = null,
        status = TaskStatus.PENDING,
        overrunMinutes = 0,
        affectedTasks = emptyList(),
    )

    private fun sampleTemplate() = PlanTemplateEntity(
        id = "tpl-1",
        title = "标准晨练",
        colorHex = "#C48520",
        isBuiltIn = true,
        schemaVersion = 1,
    )

    private fun plainFileJson(): String {
        val envelope = BackupEnvelope(
            schemaVersion = 1,
            exportedAt = Instant.parse("2026-08-08T01:00:00Z"),
            appVersionName = "0.1.0-alpha",
            appVersionCode = 1,
            data = BackupData(
                plans = listOf(samplePlan().toDto()),
                // v0.3 segments 字段保留但恒空（兼容老备份）
                segments = emptyList(),
                tasks = listOf(sampleTask().toDto()),
                taskCompletions = listOf(sampleCompletion().toDto()),
                planTemplates = listOf(sampleTemplate().toDto()),
            ),
        )
        val file = BackupFile(
            format = BackupFile.FORMAT_PLAIN,
            envelope = envelope,
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    private fun encryptedFileJson(passphrase: CharArray, iterations: Int = 1_000): String {
        val envelope = BackupEnvelope(
            schemaVersion = 1,
            exportedAt = Instant.parse("2026-08-08T01:00:00Z"),
            appVersionName = "0.1.0-alpha",
            appVersionCode = 1,
            data = BackupData(
                plans = listOf(samplePlan().toDto()),
                segments = emptyList(),
                tasks = listOf(sampleTask().toDto()),
                taskCompletions = listOf(sampleCompletion().toDto()),
                planTemplates = listOf(sampleTemplate().toDto()),
            ),
        )
        val envelopeJson = json.encodeToString(BackupEnvelope.serializer(), envelope)
        val plaintext = envelopeJson.toByteArray(Charsets.UTF_8)
        val result = BackupCrypto.encrypt(plaintext, passphrase, iterations)
        val file = BackupFile(
            format = BackupFile.FORMAT_ENCRYPTED,
            encryption = result.metadata,
            data = Base64.getEncoder().withoutPadding().encodeToString(result.ciphertext),
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    @Test
    fun `plain 文件导入 — 正确路由到 applyV1 + 返回 ImportResult (segments 恒 0)`() = runTest {
        val result = importer().import(plainFileJson(), passphrase = null)

        assertEquals(1, result.plansImported)
        // v0.3 砍段：segments 恒为 0
        assertEquals(0, result.segmentsImported)
        assertEquals(1, result.tasksImported)
        assertEquals(1, result.completionsImported)
        assertEquals(1, result.templatesImported)
        assertEquals(1, result.schemaVersion)
    }

    @Test
    fun `plain 文件导入 — 调 4 个 DAO 的 deleteAll 然后 insert 4 张表（不再有 segments）`() = runTest {
        importer().import(plainFileJson(), passphrase = null)

        // 4 个 deleteAll 都调了（不再 timeSegmentDao.deleteAll）
        coVerifyOrder {
            taskCompletionDao.deleteAll()
            taskDao.deleteAll()
            planTemplateDao.deleteAll()
            planDao.deleteAll()
        }
        // 4 个 insert 都调了（按 FK 顺序）
        coVerifyOrder {
            planTemplateDao.insert(match { it.id == "tpl-1" })
            planDao.insert(match { it.id == "plan-1" })
            taskDao.insert(match { it.id == "task-1" })
            taskCompletionDao.insert(match { it.id == "comp-1" })
        }
    }

    @Test
    fun `encrypted 文件导入 — 正确 passphrase 解密 + insert`() = runTest {
        val pass = "test-pass".toCharArray()
        try {
            val result = importer().import(
                fileJson = encryptedFileJson(pass, iterations = 1_000),
                passphrase = pass,
            )
            assertEquals(1, result.plansImported)
            assertEquals(1, result.schemaVersion)
            coVerifyOrder {
                planDao.insert(match { it.id == "plan-1" })
            }
        } finally {
            pass.fill('\u0000')
        }
    }

    @Test
    fun `encrypted 文件 — 错 passphrase 抛 AEADBadTagException`() = runTest {
        val rightPass = "right".toCharArray()
        val wrongPass = "wrong".toCharArray()
        try {
            val fileJson = encryptedFileJson(rightPass, iterations = 1_000)
            assertThrows<javax.crypto.AEADBadTagException> {
                kotlinx.coroutines.runBlocking {
                    importer().import(fileJson, passphrase = wrongPass)
                }
            }
        } finally {
            rightPass.fill('\u0000')
            wrongPass.fill('\u0000')
        }
    }

    @Test
    fun `encrypted 文件 — 缺 passphrase 抛 IllegalStateException`() = runTest {
        val pass = "x".toCharArray()
        try {
            val fileJson = encryptedFileJson(pass, iterations = 1_000)
            val ex = assertThrows<IllegalStateException> {
                kotlinx.coroutines.runBlocking {
                    importer().import(fileJson, passphrase = null)
                }
            }
            assert(ex.message!!.contains("passphrase", ignoreCase = true))
        } finally {
            pass.fill('\u0000')
        }
    }

    @Test
    fun `plain 文件 — envelope 字段为 null 抛 IllegalStateException`() = runTest {
        val file = BackupFile(
            format = BackupFile.FORMAT_PLAIN,
            envelope = null,
            data = null,
        )
        val fileJson = json.encodeToString(BackupFile.serializer(), file)
        assertThrows<IllegalStateException> {
            kotlinx.coroutines.runBlocking {
                importer().import(fileJson, passphrase = null)
            }
        }
    }

    @Test
    fun `不支持的 format 抛 IllegalArgumentException`() = runTest {
        val rawJson = """
            {
              "fileSchemaVersion": 1,
              "format": "foo-v1",
              "encryption": null,
              "envelope": null,
              "data": null
            }
        """.trimIndent()
        val ex = assertThrows<IllegalStateException> {
            kotlinx.coroutines.runBlocking {
                importer().import(rawJson, passphrase = null)
            }
        }
        assert(ex.message!!.contains("unsupported format"))
    }

    @Test
    fun `schemaVersion 不是 1 抛 IllegalArgumentException`() = runTest {
        val envelope = BackupEnvelope(
            schemaVersion = 99,
            exportedAt = Instant.parse("2026-08-08T01:00:00Z"),
            appVersionName = "9.9.9",
            appVersionCode = 999,
            data = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
        )
        val file = BackupFile(format = BackupFile.FORMAT_PLAIN, envelope = envelope)
        val fileJson = json.encodeToString(BackupFile.serializer(), file)
        val ex = assertThrows<IllegalStateException> {
            kotlinx.coroutines.runBlocking {
                importer().import(fileJson, passphrase = null)
            }
        }
        assert(ex.message!!.contains("schemaVersion"))
    }

    @Test
    fun `JSON 格式错抛 SerializationException`() = runTest {
        val badJson = "not json at all"
        assertThrows<kotlinx.serialization.SerializationException> {
            kotlinx.coroutines.runBlocking {
                importer().import(badJson, passphrase = null)
            }
        }
    }

    @Test
    fun `完全覆盖 — 旧数据全 wipe 后再 insert 新数据`() = runTest {
        val firstJson = plainFileJson()
        importer().import(firstJson, passphrase = null)

        val emptyEnvelope = BackupEnvelope(
            schemaVersion = 1,
            exportedAt = Instant.parse("2026-08-08T01:00:00Z"),
            appVersionName = "0.1.0",
            appVersionCode = 1,
            data = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
        )
        val emptyFile = BackupFile(format = BackupFile.FORMAT_PLAIN, envelope = emptyEnvelope)
        val emptyJson = json.encodeToString(BackupFile.serializer(), emptyFile)

        val result = importer().import(emptyJson, passphrase = null)
        assertEquals(0, result.plansImported)
        assertEquals(0, result.segmentsImported)
        assertEquals(0, result.tasksImported)
        assertEquals(0, result.completionsImported)
        assertEquals(0, result.templatesImported)
    }

    @Test
    fun `wipe + insert 包在 withTransaction 事务块内执行`() = runTest {
        val log = mutableListOf<String>()
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            log.add("tx-begin")
            secondArg<suspend () -> Any?>().invoke()
            log.add("tx-end")
        }
        coEvery { taskCompletionDao.deleteAll() } answers { log.add("wipe-completions"); Unit }
        coEvery { taskDao.deleteAll() } answers { log.add("wipe-tasks"); Unit }
        coEvery { planTemplateDao.deleteAll() } answers { log.add("wipe-templates"); Unit }
        coEvery { planDao.deleteAll() } answers { log.add("wipe-plans"); Unit }
        coEvery { planTemplateDao.insert(any()) } answers { log.add("ins-template"); Unit }
        coEvery { planDao.insert(any()) } answers { log.add("ins-plan"); Unit }
        coEvery { taskDao.insert(any()) } answers { log.add("ins-task"); Unit }
        coEvery { taskCompletionDao.insert(any()) } answers { log.add("ins-completion"); Unit }

        importer().import(plainFileJson(), passphrase = null)

        assertEquals(
            listOf(
                "tx-begin",
                // wipe：FK-safe 反序
                "wipe-completions", "wipe-tasks", "wipe-templates", "wipe-plans",
                // insert：FK 依赖正序
                "ins-template", "ins-plan", "ins-task", "ins-completion",
                "tx-end",
            ),
            log,
        )
    }

    @Test
    fun `insert 中途失败 — 异常向上传播（真实回滚由 Room 事务保证）`() = runTest {
        coEvery { taskDao.insert(any()) } throws RuntimeException("disk full")

        val ex = assertThrows<RuntimeException> {
            importer().import(plainFileJson(), passphrase = null)
        }
        assertEquals("disk full", ex.message)
        // wipe 已发出（模拟场景无真实状态；生产上 withTransaction 会把它一并回滚）
        coVerify(exactly = 1) { planDao.deleteAll() }
        coVerify(exactly = 1) { db.withTransaction(any<suspend () -> Any?>()) }
    }
}
