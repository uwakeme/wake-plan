package com.wakeplan.app.ui.today

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.notification.ScheduleRefresher
import com.wakeplan.app.notification.StartingAlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * TodayViewModel 快速录入测试 — v0.4
 *
 * - 合法输入：create 任务（ONCE 重复）+ refresh(today) 铺实例重排闹钟
 * - 非法输入（标题空 / 时间窗反了）：不 create 不 refresh
 * - 保存前冲突预览：报出对方计划名 / 任务名 / 重叠分钟（与卡片红条同一套检测）
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
    private val completionNotifier: com.wakeplan.app.notification.CompletionNotifier = mockk(relaxed = true)
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
        cascadeDelayCalculator = com.wakeplan.app.domain.usecase.CascadeDelayCalculator(),
        conflictDetector = com.wakeplan.app.domain.usecase.ConflictDetector(),
        completeTaskUseCase = com.wakeplan.app.domain.usecase.CompleteTaskUseCase(
            taskCompletionRepository = taskCompletionRepo,
            taskRepository = taskRepo,
            cascadeDelayCalculator = com.wakeplan.app.domain.usecase.CascadeDelayCalculator(),
            scheduleRefresher = scheduleRefresher,
            completionNotifier = completionNotifier,
            clock = fixedClock,
        ),
        startingAlarmScheduler = startingAlarmScheduler,
        scheduleRefresher = scheduleRefresher,
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

    // ---- 保存前冲突预览 ----

    private fun plan(id: String, title: String) = Plan(
        id = id,
        title = title,
        colorHex = "#F5A623",
        startDate = LocalDate.of(2026, 8, 8),
        isActive = true,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    /** 已存在的任务：MorningRun / TaskB，今天 09:00–09:30 */
    private fun stubTodayTaskB() {
        every { planRepo.observeAll() } returns flowOf(listOf(plan("plan-1", "MorningRun"), plan("plan-2", "Reading")))
        every { taskRepo.observeAll() } returns flowOf(
            listOf(
                Task(
                    id = "task-b",
                    planId = "plan-1",
                    title = "TaskB",
                    startMinutes = 9 * 60,
                    endMinutes = 9 * 60 + 30,
                    repeatRule = RepeatRule.ONCE,
                    orderInPlan = 0,
                ),
            ),
        )
        every { taskCompletionRepo.observeByDateRangeInclusive(any(), any()) } returns flowOf(
            listOf(
                TaskCompletion(
                    id = "c-b",
                    taskId = "task-b",
                    planId = "plan-1",
                    scheduledDate = LocalDate.of(2026, 8, 8),
                    plannedStart = LocalDateTime.of(2026, 8, 8, 9, 0),
                    plannedEnd = LocalDateTime.of(2026, 8, 8, 9, 30),
                ),
            ),
        )
    }

    /** uiState 是 WhileSubscribed，预览前要挂订阅者把数据流驱动起来 */
    private suspend fun kotlinx.coroutines.test.TestScope.collectUiState(vm: TodayViewModel) {
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `冲突预览 — 报出对方计划名任务名和重叠分钟`() = runTest(testDispatcher) {
        stubTodayTaskB()
        val vm = viewModel()
        collectUiState(vm)

        val preview = vm.previewQuickAddConflict("plan-2", 9 * 60, 10 * 60)

        assertEquals("MorningRun", preview?.planTitle)
        assertEquals("TaskB", preview?.taskTitle)
        assertEquals(30, preview?.overlapMinutes)
    }

    @Test
    fun `冲突预览 — 无重叠返回 null`() = runTest(testDispatcher) {
        stubTodayTaskB()
        val vm = viewModel()
        collectUiState(vm)

        assertNull(vm.previewQuickAddConflict("plan-2", 10 * 60, 10 * 60 + 30))
    }

    @Test
    fun `冲突预览 — 时间窗非法返回 null`() = runTest(testDispatcher) {
        stubTodayTaskB()
        val vm = viewModel()
        collectUiState(vm)

        assertNull(vm.previewQuickAddConflict("plan-2", 10 * 60, 10 * 60))
    }
}
