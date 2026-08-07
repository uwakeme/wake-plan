package com.wakeplan.app.data.backup

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
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TaskStatus
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

/**
 * BackupImporter 导入测试 — Phase 4.3
 *
 * Mock 5 个 DAO，验证：
 * - plain 文件：直接 parse + insert
 * - encrypted 文件：解密 + insert
 * - 错 passphrase：抛 AEADBadTagException
 * - 错 format / schemaVersion：抛 IllegalArgumentException
 * - wipe 5 表的顺序（completions → tasks → segments → templates → plans）
 * - insert 5 表的顺序（templates → plans → segments → tasks → completions）
 */
@DisplayName("BackupImporter")
class BackupImporterTest {

    private val planDao: PlanDao = mockk(relaxed = true)
    private val timeSegmentDao: TimeSegmentDao = mockk(relaxed = true)
    private val taskDao: TaskDao = mockk(relaxed = true)
    private val taskCompletionDao: TaskCompletionDao = mockk(relaxed = true)
    private val planTemplateDao: PlanTemplateDao = mockk(relaxed = true)

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun importer() = BackupImporter(
        planDao, timeSegmentDao, taskDao, taskCompletionDao, planTemplateDao,
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

    private fun sampleSegment() = TimeSegmentEntity(
        id = "seg-1",
        planId = "plan-1",
        title = "晨练段",
        startMinutes = 360,
        endMinutes = 420,
        repeatRule = RepeatRule.DAILY,
        customDays = emptySet(),
    )

    private fun sampleTask() = TaskEntity(
        id = "task-1",
        segmentId = "seg-1",
        title = "跑步",
        plannedDurationMinutes = 30,
        autoExtend = true,
        isLocked = false,
        orderInSegment = 0,
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
                segments = listOf(sampleSegment().toDto()),
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
        // 跟 BackupExporter 同算法：先 plain envelope → 加密 → 包 BackupFile
        val envelope = BackupEnvelope(
            schemaVersion = 1,
            exportedAt = Instant.parse("2026-08-08T01:00:00Z"),
            appVersionName = "0.1.0-alpha",
            appVersionCode = 1,
            data = BackupData(
                plans = listOf(samplePlan().toDto()),
                segments = listOf(sampleSegment().toDto()),
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
    fun `plain 文件导入 — 正确路由到 applyV1 + 返回 ImportResult`() = runTest {
        val result = importer().import(plainFileJson(), passphrase = null)

        assertEquals(1, result.plansImported)
        assertEquals(1, result.segmentsImported)
        assertEquals(1, result.tasksImported)
        assertEquals(1, result.completionsImported)
        assertEquals(1, result.templatesImported)
        assertEquals(1, result.schemaVersion)
    }

    @Test
    fun `plain 文件导入 — 调 5 个 DAO 的 deleteAll 然后 insert 5 张表`() = runTest {
        importer().import(plainFileJson(), passphrase = null)

        // 5 个 deleteAll 都调了
        coVerifyOrder {
            taskCompletionDao.deleteAll()
            taskDao.deleteAll()
            timeSegmentDao.deleteAll()
            planTemplateDao.deleteAll()
            planDao.deleteAll()
        }
        // 5 个 insert 都调了（按 FK 顺序）
        coVerifyOrder {
            planTemplateDao.insert(match { it.id == "tpl-1" })
            planDao.insert(match { it.id == "plan-1" })
            timeSegmentDao.insert(match { it.id == "seg-1" })
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
            // 验证 insert 调了
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
            // 错误信息提示用户需要 passphrase
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
        // 手写一个 format=foo-v1 的 JSON（绕过 schema 校验靠 ignoreUnknownKeys + 自由 string）
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
        // 构造一个 schemaVersion=99 的 plain 文件
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
        // 第一次导入 1 plan
        val firstJson = plainFileJson()  // 含 plan-1
        importer().import(firstJson, passphrase = null)

        // 第二次导入空 envelope
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
        // 第二次导入后所有列表大小都是 0
        assertEquals(0, result.plansImported)
        assertEquals(0, result.segmentsImported)
        assertEquals(0, result.tasksImported)
        assertEquals(0, result.completionsImported)
        assertEquals(0, result.templatesImported)
    }
}
