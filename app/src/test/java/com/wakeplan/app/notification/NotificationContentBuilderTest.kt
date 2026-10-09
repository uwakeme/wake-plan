package com.wakeplan.app.notification

import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * NotificationContentBuilder 单测 — Phase 3.1，v0.4.1 文案模板改为注入
 *
 * 纯 Kotlin 测试，不依赖 Android framework（NotificationTexts 直接构造中文模板，
 * 与 values/strings.xml 的 zh 文案一致）
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

    // 与 values/strings.xml（zh）一一对应的模板
    private val zhTexts = NotificationTexts(
        startingLead = "%1\$d 分钟后开始",
        startingImminent = "即将开始",
        overdueFirst = "%1\$s 已超时",
        overdueRepeat = "%1\$s 仍超时 (%2\$d/3)",
        overdueBody = "计划结束时间 %1\$s 已过",
        completedTitle = "%1\$s 已完成",
        completedOnTime = "完成于 %1\$s · 按时完成",
        completedOverrun = "完成于 %1\$s · 超时 %2\$d 分钟",
        recapTitle = "今日回顾",
        recapBody = "今天完成 %1\$d/%2\$d 个任务（%3\$d%%）",
    )

    @Nested
    @DisplayName("starting 通知")
    inner class Starting {
        @Test
        fun `lead 大于 0 时文案包含「X 分钟后开始」`() {
            val content = NotificationContentBuilder.buildStarting(completion, "晨练", 5, zhTexts)
            assertEquals(Channel.TASK_STARTING.id, content.channelId)
            assertEquals("晨练", content.title)
            assertTrue("5 分钟后开始" in content.text, "应包含 5 分钟后开始: ${content.text}")
            assertTrue("09:00" in content.text)
            assertTrue("09:30" in content.text)
        }

        @Test
        fun `lead 等于 0 时文案使用「即将开始」`() {
            val content = NotificationContentBuilder.buildStarting(completion, "晨练", 0, zhTexts)
            assertTrue("即将开始" in content.text, "lead=0 应使用「即将开始」: ${content.text}")
        }

        @Test
        fun `lead 等于 10 正确显示 10 分钟`() {
            val content = NotificationContentBuilder.buildStarting(completion, "晨练", 10, zhTexts)
            assertTrue("10 分钟后开始" in content.text)
        }
    }

    @Nested
    @DisplayName("overdue 通知")
    inner class Overdue {
        @Test
        fun `overdueCount=1 时标题「已超时」（不带次数）`() {
            val content = NotificationContentBuilder.buildOverdue(completion, "晨练", 1, zhTexts)
            assertEquals(Channel.TASK_OVERDUE.id, content.channelId)
            assertEquals("晨练 已超时", content.title)
            assertTrue("09:30" in content.text)
        }

        @Test
        fun `overdueCount=2 标题带「仍超时」并显示次数`() {
            val content = NotificationContentBuilder.buildOverdue(completion, "晨练", 2, zhTexts)
            assertEquals("晨练 仍超时 (2/3)", content.title)
        }

        @Test
        fun `overdueCount=3 标题带「仍超时」并显示 3 之 3`() {
            val content = NotificationContentBuilder.buildOverdue(completion, "晨练", 3, zhTexts)
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
            val content = NotificationContentBuilder.buildCompleted(done, "晨练", zhTexts)
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
            val content = NotificationContentBuilder.buildCompleted(done, "晨练", zhTexts)
            assertTrue("超时 25 分钟" in content.text, "${content.text}")
            assertTrue("09:55" in content.text)
        }

        @Test
        fun `actualEnd 缺失时回退 plannedEnd`() {
            val done = completion.copy(status = TaskStatus.COMPLETED, actualEnd = null)
            val content = NotificationContentBuilder.buildCompleted(done, "晨练", zhTexts)
            assertTrue("09:30" in content.text)
        }
    }

    @Nested
    @DisplayName("recap 每日回顾 (v0.5)")
    inner class Recap {
        @Test
        fun `标题固定，正文带完成数分母与百分比`() {
            val content = NotificationContentBuilder.buildRecap(completedCount = 3, expectedCount = 4, texts = zhTexts)
            assertEquals(Channel.PLAN_RECAP.id, content.channelId)
            assertEquals("今日回顾", content.title)
            assertEquals("今天完成 3/4 个任务（75%）", content.text)
        }

        @Test
        fun `全部完成显示 100 percent`() {
            val content = NotificationContentBuilder.buildRecap(completedCount = 5, expectedCount = 5, texts = zhTexts)
            assertEquals("今天完成 5/5 个任务（100%）", content.text)
        }

        @Test
        fun `分母为 0 时百分比兜底为 0 不崩（Worker 层已保证不推送，纯函数防御）`() {
            val content = NotificationContentBuilder.buildRecap(completedCount = 0, expectedCount = 0, texts = zhTexts)
            assertEquals("今天完成 0/0 个任务（0%）", content.text)
        }
    }

    @Test
    fun `跨午夜段 — completion 跨日，文案仍能格式化（不会出现负分钟）`() {
        val cross = completion.copy(
            plannedStart = date.atTime(23, 0),
            plannedEnd = date.plusDays(1).atTime(1, 0),
        )
        val content = NotificationContentBuilder.buildStarting(cross, "夜班", 5, zhTexts)
        assertTrue("23:00" in content.text)
        assertTrue("01:00" in content.text)
    }
}
