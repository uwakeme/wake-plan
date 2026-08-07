package com.wakeplan.app.data.backup

import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskCompletionEntity
import com.wakeplan.app.data.db.entity.TaskEntity
import com.wakeplan.app.data.db.entity.TimeSegmentEntity
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * BackupMapper 双向 round-trip — Phase 4.1
 *
 * 验证 entity → DTO → entity / DTO → entity → DTO 两轮都不丢字段
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
    fun `TimeSegmentEntity round-trip 含 WEEKDAYS + customDays`() {
        val original = TimeSegmentEntity(
            id = "seg-1",
            planId = "plan-1",
            title = "晨练段",
            startMinutes = 360,
            endMinutes = 420,
            repeatRule = RepeatRule.WEEKDAYS,
            customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `TimeSegmentEntity round-trip DAILY + 空 customDays`() {
        val original = TimeSegmentEntity(
            id = "seg-2",
            planId = "plan-1",
            title = null,
            startMinutes = 0,
            endMinutes = 1440,
            repeatRule = RepeatRule.DAILY,
            customDays = emptySet(),
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `TaskEntity round-trip 含 note 和 isLocked`() {
        val original = TaskEntity(
            id = "task-1",
            segmentId = "seg-1",
            title = "跑步",
            plannedDurationMinutes = 30,
            autoExtend = true,
            isLocked = true,
            orderInSegment = 0,
            note = "5km 慢跑, 心率 < 150",
        )
        val roundTrip = original.toDto().toEntity()
        assertEquals(original, roundTrip)
    }

    @Test
    fun `TaskEntity round-trip note null`() {
        val original = TaskEntity(
            id = "task-2",
            segmentId = "seg-1",
            title = "拉伸",
            plannedDurationMinutes = 10,
            autoExtend = false,
            isLocked = false,
            orderInSegment = 1,
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
}
