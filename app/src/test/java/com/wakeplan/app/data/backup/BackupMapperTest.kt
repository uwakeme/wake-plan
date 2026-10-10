package com.wakeplan.app.data.backup

import com.wakeplan.app.data.db.entity.FocusSessionEntity
import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity
import com.wakeplan.app.domain.model.FocusOutcome
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * BackupMapper 双向 round-trip — v0.3 扁平化
 *
 * v0.3 砍 time_segments 表，TimeSegmentEntity round-trip 删掉。
 * TaskEntity 字段重定义：planId / startMinutes / endMinutes / repeatRule / customDays / isLocked / orderInPlan / note。
 */
@DisplayName("BackupMapper")
class BackupMapperTest {

    @Test
    fun `PlanEntity round-trip 不丢字段`() {
        val original = PlanEntity(
            id = "plan-1",
            title = "晨练",
            colorHex = "#C48520",
            startDate = LocalDate.of(2026, 8, 1),
            isActive = true,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-08-07T12:00:00Z"),
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `TaskEntity round-trip 含 WEEKDAYS + customDays + note + isLocked`() {
        val original = TaskEntity(
            id = "task-1",
            planId = "plan-1",
            title = "跑步",
            startMinutes = 6 * 60,
            endMinutes = 6 * 60 + 30,
            repeatRule = RepeatRule.WEEKDAYS,
            customDays = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY),
            isLocked = true,
            orderInPlan = 0,
            note = "5km 慢跑, 心率 < 150",
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `TaskEntity round-trip DAILY + 空 customDays + note null`() {
        val original = TaskEntity(
            id = "task-2",
            planId = "plan-1",
            title = "拉伸",
            startMinutes = 6 * 60 + 30,
            endMinutes = 6 * 60 + 40,
            repeatRule = RepeatRule.DAILY,
            customDays = emptySet(),
            isLocked = false,
            orderInPlan = 1,
            note = null,
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `TaskCompletionEntity round-trip COMPLETED + overrun + affectedTasks`() {
        val original = TaskCompletionEntity(
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
            affectedTasks = listOf("task-2", "task-3"),
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `TaskCompletionEntity round-trip PENDING + actualStart null`() {
        val original = TaskCompletionEntity(
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
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `PlanTemplateEntity round-trip`() {
        val original = PlanTemplateEntity(
            id = "tpl-1",
            title = "标准晨练",
            colorHex = "#C48520",
            isBuiltIn = true,
            schemaVersion = 1,
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `FocusSessionEntity round-trip 不丢字段`() {
        // v0.6：专注记录此前不进备份，换机即丢——纳入备份后必须完整往返
        val original = FocusSessionEntity(
            id = "focus-1",
            completionId = "comp-1",
            planId = "plan-1",
            startedAt = Instant.parse("2026-08-08T02:00:00Z"),
            endedAt = Instant.parse("2026-08-08T02:25:00Z"),
            plannedMinutes = 25,
            actualMinutes = 25,
            outcome = FocusOutcome.FINISHED,
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `老备份文件无 focusSessions 字段时按空 list 解析`() {
        // 兼容性：v0.6 之前导出的备份文件没有这个 key，导入不能炸
        val json = """
            {"plans":[],"tasks":[],"taskCompletions":[],"planTemplates":[]}
        """.trimIndent()
        val data = testJson.decodeFromString(BackupData.serializer(), json)
        assertTrue(data.focusSessions.isEmpty())
    }

    @Test
    fun `focusSessions 序列化后可反序列化回来`() {
        val json = testJson.encodeToString(
            BackupData.serializer(),
            BackupData(
                plans = emptyList(),
                tasks = emptyList(),
                taskCompletions = emptyList(),
                planTemplates = emptyList(),
                focusSessions = listOf(
                    BackupFocusSession(
                        id = "focus-1",
                        completionId = "comp-1",
                        planId = "plan-1",
                        startedAt = Instant.parse("2026-08-08T02:00:00Z"),
                        endedAt = Instant.parse("2026-08-08T02:25:00Z"),
                        plannedMinutes = 25,
                        actualMinutes = 25,
                        outcome = FocusOutcome.ABANDONED,
                    ),
                ),
            ),
        )
        val decoded = testJson.decodeFromString(BackupData.serializer(), json)
        assertEquals(1, decoded.focusSessions.size)
        assertEquals(FocusOutcome.ABANDONED, decoded.focusSessions.first().outcome)
        assertEquals(Instant.parse("2026-08-08T02:00:00Z"), decoded.focusSessions.first().startedAt)
    }

    private companion object {
        /** 与 BackupExporter/BackupImporter 生产配置一致 */
        val testJson = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
