package com.wakeplan.app.widget

import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * WidgetDataRefresher 刷新信号测试 — v0.4
 *
 * 纯 flow 逻辑（Android 壳不测）：
 * - 订阅后先收到一次初始信号
 * - 任一数据源变化再发信号
 * - 300ms 防抖窗口内的连续变化合并成一次
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("WidgetDataRefresher 刷新信号")
class WidgetDataRefresherTest {

    private val date = LocalDate.of(2026, 9, 11)

    private fun completion(id: String) = TaskCompletion(
        id = id,
        taskId = "t-$id",
        planId = "plan-1",
        scheduledDate = date,
        plannedStart = date.atTime(9, 0),
        plannedEnd = date.atTime(9, 30),
        status = TaskStatus.PENDING,
        overrunMinutes = 0,
        affectedTasks = emptyList(),
    )

    @Test
    fun `订阅即有初始信号，数据变化后再发一次`() = runTest {
        val completions = MutableStateFlow(listOf(completion("c-1")))
        val plans = MutableStateFlow(emptyList<com.wakeplan.app.domain.model.Plan>())
        val tasks = MutableStateFlow(emptyList<com.wakeplan.app.domain.model.Task>())

        val signals = mutableListOf<Unit>()
        val job = launch {
            widgetRefreshSignal(completions, plans, tasks).collect { signals += it }
        }

        advanceTimeBy(400)
        runCurrent()
        completions.value = listOf(completion("c-1"), completion("c-2"))
        advanceTimeBy(400)
        runCurrent()

        // 初始 1 次 + 变化 1 次
        assertEquals(2, signals.size)
        job.cancelAndJoin()
    }

    @Test
    fun `防抖窗口内连续两次变化合并成一次信号`() = runTest {
        val completions = MutableStateFlow(emptyList<TaskCompletion>())
        val plans = MutableStateFlow(emptyList<com.wakeplan.app.domain.model.Plan>())
        val tasks = MutableStateFlow(emptyList<com.wakeplan.app.domain.model.Task>())

        val signals = mutableListOf<Unit>()
        val job = launch {
            widgetRefreshSignal(completions, plans, tasks).collect { signals += it }
        }

        advanceTimeBy(400)
        runCurrent()
        val before = signals.size
        // 防抖窗口（300ms）内连写两条 —— 模拟铺实例 / 级联延期
        completions.value = listOf(completion("c-1"))
        advanceTimeBy(100)
        completions.value = listOf(completion("c-1"), completion("c-2"))
        advanceTimeBy(400)
        runCurrent()

        assertEquals(before + 1, signals.size)
        job.cancelAndJoin()
    }
}
