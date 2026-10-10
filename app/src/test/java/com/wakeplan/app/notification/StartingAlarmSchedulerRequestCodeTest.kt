package com.wakeplan.app.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * StartingAlarmScheduler.requestCodeFor — requestCode 取值域防护
 *
 * hashCode() 返回 Int，取值域含负数。负 requestCode 在部分 ROM 上行为不一致，
 * 这里锁定"始终非负"这条契约。
 */
@DisplayName("StartingAlarmScheduler requestCode")
class StartingAlarmSchedulerRequestCodeTest {

    @Test
    fun `requestCode 始终非负`() {
        // 构造一批 hashCode 为负的 completionId（真实 UUID 会出现）
        val ids = (1..500).map { "id-$it" }
        ids.forEach { id ->
            assertTrue(
                StartingAlarmScheduler.requestCodeFor(id) >= 0,
                "requestCode 必须非负，但 $id 得到 ${StartingAlarmScheduler.requestCodeFor(id)}",
            )
        }
    }

    @Test
    fun `相同 id 得到相同 requestCode`() {
        assertEquals(
            StartingAlarmScheduler.requestCodeFor("comp-1"),
            StartingAlarmScheduler.requestCodeFor("comp-1"),
        )
    }

    @Test
    fun `不同 id 大概率得到不同 requestCode`() {
        // 碰撞概率已被技术债清单接受，这里只保证常见 id 不撞
        val a = StartingAlarmScheduler.requestCodeFor("comp-1")
        val b = StartingAlarmScheduler.requestCodeFor("comp-2")
        assertTrue(a != b, "comp-1 与 comp-2 不应碰撞")
    }
}
