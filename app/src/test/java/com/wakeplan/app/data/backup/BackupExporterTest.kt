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
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * BackupExporter 编排测试 — Phase 4.1
 *
 * Mock 5 个 DAO 返回固定数据，验证 export() 输出的 JSON 能反序列化回原 envelope
 * 端到端测：DAO read → mapper → serializer → JSON → deserialize
 */
@DisplayName("BackupExporter")
class BackupExporterTest {

    private val planDao: PlanDao = mockk()
    private val timeSegmentDao: TimeSegmentDao = mockk()
    private val taskDao: TaskDao = mockk()
    private val taskCompletionDao: TaskCompletionDao = mockk()
    private val planTemplateDao: PlanTemplateDao = mockk()

    // 固定 clock → exportedAt 确定性
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC)
    private val appVersionProvider = object : AppVersionProvider {
        override val versionName = "0.1.0-alpha"
        override val versionCode = 1
    }

    private fun exporter() = BackupExporter(
        planDao = planDao,
        timeSegmentDao = timeSegmentDao,
        taskDao = taskDao,
        taskCompletionDao = taskCompletionDao,
        planTemplateDao = planTemplateDao,
        clock = fixedClock,
        appVersionProvider = appVersionProvider,
    )

    private val samplePlan = PlanEntity(
        id = "plan-1",
        title = "晨练",
        colorHex = "#C48520",
        startDate = LocalDate.of(2026, 8, 1),
        isActive = true,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-07T12:00:00Z"),
    )

    private val sampleSegment = TimeSegmentEntity(
        id = "seg-1",
        planId = "plan-1",
        title = "晨练段",
        startMinutes = 360,
        endMinutes = 420,
        repeatRule = RepeatRule.WEEKDAYS,
        customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    )

    private val sampleTask = TaskEntity(
        id = "task-1",
        segmentId = "seg-1",
        title = "跑步",
        plannedDurationMinutes = 30,
        autoExtend = true,
        isLocked = false,
        orderInSegment = 0,
        note = null,
    )

    private val sampleCompletion = TaskCompletionEntity(
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

    private val sampleTemplate = PlanTemplateEntity(
        id = "tpl-1",
        title = "标准晨练",
        colorHex = "#C48520",
        isBuiltIn = true,
        schemaVersion = 1,
    )

    @Test
    fun `空数据库 export 出来 5 个列表都为空`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(emptyList())
        coEvery { timeSegmentDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskCompletionDao.getAllSnapshot() } returns emptyList()
        coEvery { planTemplateDao.observeAll() } returns flowOf(emptyList())

        val json = exporter().export()
        val envelope = Json.decodeFromString(BackupEnvelope.serializer(), json)

        assertEquals(1, envelope.schemaVersion)
        assertEquals("0.1.0-alpha", envelope.appVersionName)
        assertEquals(1, envelope.appVersionCode)
        assertEquals(Instant.parse("2026-08-08T12:00:00Z"), envelope.exportedAt)
        assertEquals(0, envelope.data.plans.size)
        assertEquals(0, envelope.data.segments.size)
        assertEquals(0, envelope.data.tasks.size)
        assertEquals(0, envelope.data.taskCompletions.size)
        assertEquals(0, envelope.data.planTemplates.size)
    }

    @Test
    fun `全表有数据时 export + deserialize round-trip 一致`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(listOf(samplePlan))
        coEvery { timeSegmentDao.observeAll() } returns flowOf(listOf(sampleSegment))
        coEvery { taskDao.observeAll() } returns flowOf(listOf(sampleTask))
        coEvery { taskCompletionDao.getAllSnapshot() } returns listOf(sampleCompletion)
        coEvery { planTemplateDao.observeAll() } returns flowOf(listOf(sampleTemplate))

        val json = exporter().export()
        val envelope = Json.decodeFromString(BackupEnvelope.serializer(), json)

        assertEquals(1, envelope.data.plans.size)
        assertEquals(samplePlan.id, envelope.data.plans[0].id)
        assertEquals(samplePlan.title, envelope.data.plans[0].title)
        assertEquals(samplePlan.colorHex, envelope.data.plans[0].colorHex)
        assertEquals(samplePlan.startDate, envelope.data.plans[0].startDate)
        assertEquals(samplePlan.isActive, envelope.data.plans[0].isActive)

        assertEquals(1, envelope.data.segments.size)
        assertEquals(RepeatRule.WEEKDAYS, envelope.data.segments[0].repeatRule)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), envelope.data.segments[0].customDays)

        assertEquals(1, envelope.data.tasks.size)
        assertEquals("跑步", envelope.data.tasks[0].title)
        assertEquals(30, envelope.data.tasks[0].plannedDurationMinutes)

        assertEquals(1, envelope.data.taskCompletions.size)
        assertEquals(TaskStatus.PENDING, envelope.data.taskCompletions[0].status)

        assertEquals(1, envelope.data.planTemplates.size)
        assertTrue(envelope.data.planTemplates[0].isBuiltIn)
    }

    @Test
    fun `export 调用 DAO 各一次取 snapshot`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(emptyList())
        coEvery { timeSegmentDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskCompletionDao.getAllSnapshot() } returns emptyList()
        coEvery { planTemplateDao.observeAll() } returns flowOf(emptyList())

        val ex = exporter()
        ex.export()

        // 5 个 DAO 各调一次 — 用 mockk 的 verify 验证
        io.mockk.coVerify(exactly = 1) { planDao.observeAll() }
        io.mockk.coVerify(exactly = 1) { timeSegmentDao.observeAll() }
        io.mockk.coVerify(exactly = 1) { taskDao.observeAll() }
        io.mockk.coVerify(exactly = 1) { taskCompletionDao.getAllSnapshot() }
        io.mockk.coVerify(exactly = 1) { planTemplateDao.observeAll() }
    }

    @Test
    fun `exportedAt 走注入的 clock（不是 system now）`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(emptyList())
        coEvery { timeSegmentDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskCompletionDao.getAllSnapshot() } returns emptyList()
        coEvery { planTemplateDao.observeAll() } returns flowOf(emptyList())

        val json = exporter().export()
        val envelope = Json.decodeFromString(BackupEnvelope.serializer(), json)
        // 固定 clock → 2026-08-08T12:00:00Z
        assertEquals(Instant.parse("2026-08-08T12:00:00Z"), envelope.exportedAt)
    }

    @Test
    fun `appVersionName 和 appVersionCode 走注入的 provider`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(emptyList())
        coEvery { timeSegmentDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskCompletionDao.getAllSnapshot() } returns emptyList()
        coEvery { planTemplateDao.observeAll() } returns flowOf(emptyList())

        val customProvider = object : AppVersionProvider {
            override val versionName = "1.2.3-test"
            override val versionCode = 999
        }
        val ex = BackupExporter(
            planDao, timeSegmentDao, taskDao, taskCompletionDao, planTemplateDao,
            fixedClock, customProvider,
        )
        val json = ex.export()
        val envelope = Json.decodeFromString(BackupEnvelope.serializer(), json)
        assertEquals("1.2.3-test", envelope.appVersionName)
        assertEquals(999, envelope.appVersionCode)
    }

    @Test
    fun `export 输出是合法 JSON 能被 kotlinx 解析`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(listOf(samplePlan))
        coEvery { timeSegmentDao.observeAll() } returns flowOf(listOf(sampleSegment))
        coEvery { taskDao.observeAll() } returns flowOf(listOf(sampleTask))
        coEvery { taskCompletionDao.getAllSnapshot() } returns listOf(sampleCompletion)
        coEvery { planTemplateDao.observeAll() } returns flowOf(listOf(sampleTemplate))

        val json = exporter().export()
        // 用 kotlinx 通用 JsonElement 解析，能解析 = 合法 JSON
        val element = Json.parseToJsonElement(json)
        val root = element.jsonObject
        assertNotNull(root["schemaVersion"])
        assertNotNull(root["data"])
        assertNotNull(root["exportedAt"])
        // 顶层 exportedAt 是字符串
        val exportedAtStr = root["exportedAt"]!!.jsonPrimitive.content
        assertTrue(exportedAtStr.contains("2026-08-08T12:00:00Z"))
    }

    // ===== Phase 4.2 exportToBackupFile =====

    @Test
    fun `exportToBackupFile 无 passphrase — format=plain-v1 envelope 内嵌`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(listOf(samplePlan))
        coEvery { timeSegmentDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskCompletionDao.getAllSnapshot() } returns emptyList()
        coEvery { planTemplateDao.observeAll() } returns flowOf(emptyList())

        val fileJson = exporter().exportToBackupFile(passphrase = null)
        val file = Json.decodeFromString(BackupFile.serializer(), fileJson)

        assertEquals(BackupFile.FORMAT_PLAIN, file.format)
        assertNull(file.encryption)
        assertNotNull(file.envelope)
        assertNull(file.data)
        assertEquals(1, file.fileSchemaVersion)
        // 内嵌 envelope 数据保持
        assertEquals(1, file.envelope!!.data.plans.size)
        assertEquals("plan-1", file.envelope!!.data.plans[0].id)
    }

    @Test
    fun `exportToBackupFile 有 passphrase — format=encrypted-v1 + encryption metadata + base64 data`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(listOf(samplePlan))
        coEvery { timeSegmentDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskCompletionDao.getAllSnapshot() } returns emptyList()
        coEvery { planTemplateDao.observeAll() } returns flowOf(emptyList())

        val pass = "test-passphrase".toCharArray()
        try {
            // 用小 iteration 加速测试
            val fileJson = exporter().exportToBackupFile(passphrase = pass, iterations = 1_000)
            val file = Json.decodeFromString(BackupFile.serializer(), fileJson)

            assertEquals(BackupFile.FORMAT_ENCRYPTED, file.format)
            assertNotNull(file.encryption)
            assertNull(file.envelope)
            assertNotNull(file.data)

            val meta = file.encryption!!
            assertEquals("PBKDF2-SHA256", meta.kdf)
            assertEquals("AES-256-GCM", meta.cipher)
            assertEquals(1_000, meta.iterations)
            // base64 长度: 16 bytes salt → 24 chars (no padding newline)
            assertTrue(meta.saltB64.length >= 22, "salt base64 should be ~24 chars, got ${meta.saltB64.length}")
            assertTrue(meta.ivB64.length >= 16, "iv base64 should be ~16 chars, got ${meta.ivB64.length}")
            // ciphertext data 是非空 base64
            assertTrue(file.data!!.isNotEmpty())

            // 关键 round-trip：解密后能拿回原 envelope
            val ciphertext = java.util.Base64.getDecoder().decode(file.data)
            val plaintextBytes = BackupCrypto.decrypt(ciphertext, meta, pass)
            val innerEnvelope = Json.decodeFromString(BackupEnvelope.serializer(), String(plaintextBytes, Charsets.UTF_8))
            assertEquals(1, innerEnvelope.data.plans.size)
            assertEquals("plan-1", innerEnvelope.data.plans[0].id)
        } finally {
            pass.fill('\u0000')
        }
    }

    @Test
    fun `exportToBackupFile 加密 — 错 passphrase 解不出来`() = runTest {
        coEvery { planDao.observeAll() } returns flowOf(emptyList())
        coEvery { timeSegmentDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskDao.observeAll() } returns flowOf(emptyList())
        coEvery { taskCompletionDao.getAllSnapshot() } returns emptyList()
        coEvery { planTemplateDao.observeAll() } returns flowOf(emptyList())

        val rightPass = "right".toCharArray()
        val wrongPass = "wrong".toCharArray()
        try {
            val fileJson = exporter().exportToBackupFile(passphrase = rightPass, iterations = 1_000)
            val file = Json.decodeFromString(BackupFile.serializer(), fileJson)
            val ciphertext = java.util.Base64.getDecoder().decode(file.data)
            val meta = file.encryption!!

            // 错 passphrase 应该抛 AEADBadTagException
            try {
                BackupCrypto.decrypt(ciphertext, meta, wrongPass)
                error("expected exception")
            } catch (e: javax.crypto.AEADBadTagException) {
                // OK
            } finally {
                wrongPass.fill('\u0000')
            }
        } finally {
            rightPass.fill('\u0000')
        }
    }
}
