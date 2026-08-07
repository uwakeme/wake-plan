package com.wakeplan.app.ui.common

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * HighlightBus 单测 — Phase 3.3
 *
 * `object` 全局单例，测试间用 @AfterEach 清理状态避免相互污染
 * 验证 emit / clear 两个公开方法
 */
@DisplayName("HighlightBus")
class HighlightBusTest {

    @AfterEach
    fun tearDown() {
        // 测试隔离 — 避免上一个测试的 emit 状态泄漏到下一个
        HighlightBus.clear()
    }

    @Test
    fun `初始状态为 null`() = runTest {
        // 注意：测试顺序不确定，每次跑前 tearDown 会清，但还是要先 clear 一次
        HighlightBus.clear()
        assertNull(HighlightBus.highlightedTaskId.first())
    }

    @Test
    fun `emit 设置 taskId 后 collect 能拿到`() = runTest {
        HighlightBus.emit("task-abc-123")
        assertEquals("task-abc-123", HighlightBus.highlightedTaskId.first())
    }

    @Test
    fun `emit 后再 emit 不同的 taskId 覆盖前值`() = runTest {
        HighlightBus.emit("task-1")
        HighlightBus.emit("task-2")
        assertEquals("task-2", HighlightBus.highlightedTaskId.first())
    }

    @Test
    fun `clear 后回到 null`() = runTest {
        HighlightBus.emit("task-xyz")
        assertEquals("task-xyz", HighlightBus.highlightedTaskId.first())
        HighlightBus.clear()
        assertNull(HighlightBus.highlightedTaskId.first())
    }

    @Test
    fun `emit 空字符串也合法（不抛异常）`() = runTest {
        HighlightBus.emit("")
        assertEquals("", HighlightBus.highlightedTaskId.first())
    }
}
