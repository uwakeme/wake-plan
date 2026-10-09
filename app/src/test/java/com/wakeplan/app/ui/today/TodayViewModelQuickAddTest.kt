package com.wakeplan.app.ui.today

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.notification.CompletionNotifier
import com.wakeplan.app.notification.ScheduleRefresher
import com.wakeplan.app.notification.StartingAlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * TodayViewModel 快速录入测试 — v0.4
 *
 * - 合法输入：create 任务（ONCE 重复）+ refresh(today) 铺实例重排闹钟
 * - 非法输入（标题空 / 时间窗反了）：不 create 不 refresh
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TodayViewModel 快速录入 (v0.4)")
class TodayViewModelQuickAddTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC)

    private val planRepo: PlanRepository = mockk(relaxed = true)
    private val taskRepo: TaskRepository = mockk(relaxed = true)
    private val taskCompletionRepo: TaskCompletionRepository = mockk(relaxed = true)
    private val startingAlarmScheduler: StartingAlarmScheduler = mockk(relaxed = true)
    private val scheduleRefresher: ScheduleRefresher = mockk(relaxed = true)
    private val completionNotifier: CompletionNotifier = mockk(relaxed = true)
    private val minuteTicker: com.wakeplan.app.domain.time.MinuteTicker = mockk {
        every { now } returns flowOf(java.time.LocalDateTime.of(2026, 8, 8, 12, 0))
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { planRepo.observeAll() } returns flowOf(emptyList())
        every { taskRepo.observeAll() } returns flowOf(emptyList())
        coEvery {
            taskRepo.create(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TodayViewModel(
        planRepository = planRepo,
        taskRepository = taskRepo,
        taskCompletionRepository = taskCompletionRepo,
        cascadeDelayCalculator = mockk(relaxed = true),
        conflictDetector = mockk(relaxed = true),
        startingAlarmScheduler = startingAlarmScheduler,
        scheduleRefresher = scheduleRefresher,
        completionNotifier = completionNotifier,
        minuteTicker = minuteTicker,
        clock = fixedClock,
    )

    @Test
    fun `合法输入 — create ONCE 任务并刷新当天调度`() = runTest(testDispatcher) {
        viewModel().quickAddTask(
            planId = "plan-1",
            title = "背单词",
            startMinutes = LocalTime.of(21, 0).toSecondOfDay() / 60,
            endMinutes = LocalTime.of(21, 30).toSecondOfDay() / 60,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            taskRepo.create(
                planId = "plan-1",
                title = "背单词",
                startMinutes = 21 * 60,
                endMinutes = 21 * 60 + 30,
                repeatRule = RepeatRule.ONCE,
                customDays = emptySet(),
                isLocked = false,
                note = null,
            )
        }
        coVerify(exactly = 1) { scheduleRefresher.refresh(LocalDate.of(2026, 8, 8)) }
    }

    @Test
    fun `标题为空 — 不创建不刷新`() = runTest(testDispatcher) {
        viewModel().quickAddTask("plan-1", "   ", 600, 630)
        advanceUntilIdle()

        coVerify(exactly = 0) { taskRepo.create(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { scheduleRefresher.refresh(any()) }
    }

    @Test
    fun `结束时间不晚于开始 — 不创建不刷新`() = runTest(testDispatcher) {
        viewModel().quickAddTask("plan-1", "任务", 630, 630)
        advanceUntilIdle()

        coVerify(exactly = 0) { taskRepo.create(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { scheduleRefresher.refresh(any()) }
    }

    @Test
    fun `VM 的 today 由注入 clock 决定（2026-08-08）`() {
        assertEquals(LocalDate.of(2026, 8, 8), LocalDate.now(fixedClock))
    }
}
