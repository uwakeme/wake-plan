package com.wakeplan.app.notification

import android.app.NotificationManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * NotificationChannels 单测 — Phase 3.1
 *
 * 纯 enum 测试，不依赖 Context / Android framework
 * createAll(context) 留到 emulator 集成测试
 */
@DisplayName("NotificationChannels")
class NotificationChannelsTest {

    @Test
    fun `5 个 channel id 唯一（v0_6 加 focus_timer）`() {
        val ids = Channel.values().map { it.id }
        assertEquals(5, ids.size)
        assertEquals(ids.size, ids.toSet().size, "channel id 必须唯一")
    }

    @Test
    fun `4 个 channel id 符合 PRD 命名（task_starting task_overdue task_completed plan_recap）`() {
        val ids = Channel.values().map { it.id }.toSet()
        assertTrue("task_starting" in ids)
        assertTrue("task_overdue" in ids)
        assertTrue("task_completed" in ids)
        assertTrue("plan_recap" in ids)
    }

    @Test
    fun `P0 渠道（starting + overdue）importance 较高，P2 渠道（completed + recap）importance 较低`() {
        val starting = Channel.TASK_STARTING
        val overdue = Channel.TASK_OVERDUE
        val completed = Channel.TASK_COMPLETED
        val recap = Channel.PLAN_RECAP

        // starting 任务开始前提醒很关键，HIGH
        assertEquals(NotificationManager.IMPORTANCE_HIGH, starting.importance)
        // overdue 提醒默认 DEFAULT
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, overdue.importance)
        // completed / recap 是 P2 (v0.1 关闭)，LOW 不打扰
        assertEquals(NotificationManager.IMPORTANCE_LOW, completed.importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW, recap.importance)
    }

    @Test
    fun `每个 channel 都有名称与描述资源（v0_4_1 起走 strings 跟随 App 语言）`() {
        Channel.values().forEach { ch ->
            assertTrue(ch.nameRes != 0, "${ch.id} nameRes 不能为 0")
            assertTrue(ch.descriptionRes != 0, "${ch.id} descriptionRes 不能为 0")
        }
    }

    @Test
    fun `starting 和 overdue 的名称资源不同`() {
        assertNotEquals(Channel.TASK_STARTING.nameRes, Channel.TASK_OVERDUE.nameRes)
    }
}
