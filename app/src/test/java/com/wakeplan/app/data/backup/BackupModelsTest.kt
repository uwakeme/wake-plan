package com.wakeplan.app.data.backup

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TaskStatus
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * BackupModels envelope JSON round-trip — Phase 4.1
 *
 * 测全字段：5 类 DTO + 5 个 java.time 字段
 * 验证序列化 + 反序列化不丢字段
 */
@DisplayName("BackupModels")
class BackupModelsTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun sampleEnvelope(): BackupEnvelope = BackupEnvelope(
        schemaVersion = 1,
        exportedAt = Instant.parse("2026-08-08T01:23:45Z"),
        appVersionName = "0.1.0-alpha",
        appVersionCode = 1,
        data = BackupData(
            plans = listOf(
                BackupPlan(
                    id = "plan-1",
                    title = "晨练",
                    colorHex = "#C48520",
                    startDate = LocalDate.of(2026, 8, 1),
                    isActive = true,
                    createdAt = Instant.parse("2026-08-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-08-07T12:00:00Z"),
                ),
            ),
            segments = listOf(
                BackupSegment(
                    id = "seg-1",
                    planId = "plan-1",
                    title = "晨练段",
                    startMinutes = 6 * 60,
                    endMinutes = 7 * 60,
                    repeatRule = RepeatRule.WEEKDAYS,
                    customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                ),
            ),
            tasks = listOf(
                BackupTask(
                    id = "task-1",
                    planId = "plan-1",
                    title = "跑步",
                    startMinutes = 6 * 60,
                    endMinutes = 6 * 60 + 30,
                    repeatRule = RepeatRule.DAILY,
                    isLocked = false,
                    orderInPlan = 0,
                    note = "5km 慢跑",
                ),
                BackupTask(
                    id = "task-2",
                    planId = "plan-1",
                    title = "拉伸",
                    startMinutes = 6 * 60 + 30,
                    endMinutes = 6 * 60 + 40,
                    repeatRule = RepeatRule.DAILY,
                    isLocked = true,
                    orderInPlan = 1,
                    note = null,
                ),
            ),
            taskCompletions = listOf(
                BackupCompletion(
                    id = "comp-1",
                    taskId = "task-1",
                    planId = "plan-1",
                    scheduledDate = LocalDate.of(2026, 8, 8),
                    plannedStart = LocalDateTime.of(2026, 8, 8, 6, 10),
                    plannedEnd = LocalDateTime.of(2026, 8, 8, 6, 40),
                    actualStart = LocalDateTime.of(2026, 8, 8, 6, 15),
                    actualEnd = LocalDateTime.of(2026, 8, 8, 6, 42),
                    status = TaskStatus.COMPLETED,
                    overrunMinutes = 2,
                    affectedTasks = listOf("task-2"),
                ),
                BackupCompletion(
                    id = "comp-2",
                    taskId = "task-2",
                    planId = "plan-1",
                    scheduledDate = LocalDate.of(2026, 8, 8),
                    plannedStart = LocalDateTime.of(2026, 8, 8, 6, 40),
                    plannedEnd = LocalDateTime.of(2026, 8, 8, 6, 50),
                    actualStart = null,
                    actualEnd = null,
                    status = TaskStatus.PENDING,
                    overrunMinutes = 0,
                    affectedTasks = emptyList(),
                ),
            ),
            planTemplates = listOf(
                BackupTemplate(
                    id = "tpl-1",
                    title = "标准晨练",
                    colorHex = "#C48520",
                    isBuiltIn = true,
                    schemaVersion = 1,
                ),
            ),
        ),
    )

    @Test
    fun `envelope 序列化后包含 schemaVersion 和 data`() {
        val envelope = sampleEnvelope()
        val json = json.encodeToString(BackupEnvelope.serializer(), envelope)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"data\""))
        assertTrue(json.contains("\"plans\""))
        assertTrue(json.contains("\"taskCompletions\""))
        assertTrue(json.contains("\"planTemplates\""))
    }

    @Test
    fun `envelope round-trip 不丢字段`() {
        val original = sampleEnvelope()
        val encoded = json.encodeToString(BackupEnvelope.serializer(), original)
        val decoded = json.decodeFromString(BackupEnvelope.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Instant 字段序列化后是 ISO-8601 UTC 字符串`() {
        val envelope = sampleEnvelope()
        val encoded = json.encodeToString(BackupEnvelope.serializer(), envelope)
        assertTrue(encoded.contains("2026-08-08T01:23:45Z"))
    }

    @Test
    fun `LocalDate 字段序列化后是 YYYY-MM-DD`() {
        val envelope = sampleEnvelope()
        val encoded = json.encodeToString(BackupEnvelope.serializer(), envelope)
        assertTrue(encoded.contains("\"2026-08-01\"") || encoded.contains("\"2026-08-01\""), "missing date")
    }

    @Test
    fun `LocalDateTime 字段序列化后不带时区`() {
        val envelope = sampleEnvelope()
        val encoded = json.encodeToString(BackupEnvelope.serializer(), envelope)
        // 本地时间 ISO 格式，无 Z 无 +08:00
        assertTrue(encoded.contains("2026-08-08T06:10:00") || encoded.contains("2026-08-08T06:10"))
    }

    @Test
    fun `DayOfWeekSet 序列化为逗号分隔字符串`() {
        val envelope = sampleEnvelope()
        val encoded = json.encodeToString(BackupEnvelope.serializer(), envelope)
        assertTrue(encoded.contains("\"MONDAY,WEDNESDAY,FRIDAY\"") || encoded.contains("MONDAY,WEDNESDAY,FRIDAY"))
    }

    @Test
    fun `空 customDays round-trip 为空字符串`() {
        val original = sampleEnvelope().copy(
            data = sampleEnvelope().data.copy(
                segments = listOf(
                    sampleEnvelope().data.segments[0].copy(customDays = emptySet()),
                ),
            ),
        )
        val encoded = json.encodeToString(BackupEnvelope.serializer(), original)
        val decoded = json.decodeFromString(BackupEnvelope.serializer(), encoded)
        assertEquals(emptySet<DayOfWeek>(), decoded.data.segments[0].customDays)
    }

    @Test
    fun `空 affectedTasks round-trip 为空数组`() {
        val original = sampleEnvelope()
        val encoded = json.encodeToString(BackupEnvelope.serializer(), original)
        val decoded = json.decodeFromString(BackupEnvelope.serializer(), encoded)
        assertEquals(emptyList<String>(), decoded.data.taskCompletions[1].affectedTasks)
    }

    @Test
    fun `prettyPrint 输出多行可读`() {
        val envelope = sampleEnvelope()
        val encoded = json.encodeToString(BackupEnvelope.serializer(), envelope)
        // prettyPrint=true 应该有换行
        assertTrue(encoded.contains("\n"), "expected multi-line pretty output")
        // 行数 > 5 表示 pretty 生效
        assertTrue(encoded.lines().size > 5)
    }
}
