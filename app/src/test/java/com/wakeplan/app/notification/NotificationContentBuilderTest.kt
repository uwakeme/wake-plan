package com.wakeplan.app.notification

import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * NotificationContentBuilder 单测 — Phase 3.1
 *
 * 纯 Kotlin 测试，不依赖 Android framework
 * 覆盖：starting (lead > 0 / = 0) + overdue (1st / 2nd 3rd) 标题文案
 */
@DisplayName("NotificationContentBuilder")
class NotificationContentBuilderTest {

    private val date = LocalDate.of(2026, 8, 10)
    private val completion = TaskCompletion(
        id = "c-1",
        taskId = "t-1",
        planId = "plan-1",
        scheduledDate = date,
        plannedStart = date.atTime(9, 0),
        plannedEnd = date.atTime(9, 30),
        status = TaskStatus.PENDING,
    )

    @Nested
    @DisplayName("starting 通知")
    inner class Starting {
        @Test
        fun `lead 大于 0 时文案包含「X 分钟后开始」`() {
            val content = NotificationContentBuilder.buildStarting(completion, "晨练", leadMinutes = 5)
            assertEquals(Channel.TASK_STARTING.id, content.channelId)
            assertEquals("晨练", content.title)
            assertTrue("5 分钟后开始" in content.text, "应包含 5 分钟后开始: ${content.text}")
            assertTrue("09:00" in content.text)
            assertTrue("09:30" in content.text)
        }

        @Test
        fun `lead 等于 0 时文案使用「即将开始」`() {
            val content = NotificationContentBuilder.buildStarting(completion, "晨练", leadMinutes = 0)
            assertTrue("即将开始" in content.text, "lead=0 应使用「即将开始」: ${content.text}")
        }

        @Test
        fun `lead 等于 10 正确显示 10 分钟`() {
            val content = NotificationContentBuilder.buildStarting(completion, "晨练", leadMinutes = 10)
            assertTrue("10 分钟后开始" in content.text)
        }
    }

    @Nested
    @DisplayName("overdue 通知")
    inner class Overdue {
        @Test
        fun `overdueCount=1 时标题「已超时」（不带次数）`() {
            val content = NotificationContentBuilder.buildOverdue(completion, "晨练", overdueCount = 1)
            assertEquals(Channel.TASK_OVERDUE.id, content.channelId)
            assertEquals("晨练 已超时", content.title)
            assertTrue("09:30" in content.text)
        }

        @Test
        fun `overdueCount=2 标题带「仍超时」并显示次数`() {
            val content = NotificationContentBuilder.buildOverdue(completion, "晨练", overdueCount = 2)
            assertEquals("晨练 仍超时 (2/3)", content.title)
        }

        @Test
        fun `overdueCount=3 标题带「仍超时」并显示 3 之 3`() {
            val content = NotificationContentBuilder.buildOverdue(completion, "晨练", overdueCount = 3)
            assertEquals("晨练 仍超时 (3/3)", content.title)
        }
    }

    @Nested
    @DisplayName("completed 通知 (v0.4)")
    inner class Completed {
        @Test
        fun `标题「X 已完成」，按时完成文案不带超时`() {
            val done = completion.copy(
                status = TaskStatus.COMPLETED,
                actualEnd = date.atTime(9, 28),
                overrunMinutes = 0,
            )
            val content = NotificationContentBuilder.buildCompleted(done, "晨练")
            assertEquals(Channel.TASK_COMPLETED.id, content.channelId)
            assertEquals("晨练 已完成", content.title)
            assertTrue("09:28" in content.text, "应显示实际完成时间: ${content.text}")
            assertTrue("按时完成" in content.text, "0 超时应显示按时: ${content.text}")
        }

        @Test
        fun `超时完成文案带超时分钟数`() {
            val done = completion.copy(
                status = TaskStatus.COMPLETED,
                actualEnd = date.atTime(9, 55),
                overrunMinutes = 25,
            )
            val content = NotificationContentBuilder.buildCompleted(done, "晨练")
            assertTrue("超时 25 分钟" in content.text, "${content.text}")
            assertTrue("09:55" in content.text)
        }

        @Test
        fun `actualEnd 缺失时回退 plannedEnd`() {
            val done = completion.copy(status = TaskStatus.COMPLETED, actualEnd = null)
            val content = NotificationContentBuilder.buildCompleted(done, "晨练")
            assertTrue("09:30" in content.text)
        }
    }

    @Test
    fun `跨午夜段 — completion 跨日，文案仍能格式化（不会出现负分钟）`() {
        val cross = completion.copy(
            plannedStart = date.atTime(23, 0),
            plannedEnd = date.plusDays(1).atTime(1, 0),
        )
        val content = NotificationContentBuilder.buildStarting(cross, "夜班", leadMinutes = 5)
        assertTrue("23:00" in content.text)
        assertTrue("01:00" in content.text)
    }
}
