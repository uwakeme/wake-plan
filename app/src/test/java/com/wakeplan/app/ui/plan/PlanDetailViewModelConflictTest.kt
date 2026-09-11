package com.wakeplan.app.ui.plan

import androidx.lifecycle.SavedStateHandle
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.usecase.ConflictDetector
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * PlanDetailViewModel 内联冲突标注测试 — v0.4（PRD US-09）
 *
 * - 本计划两个任务时间窗重叠 → todayConflicts 含这两个 taskId，重叠分钟正确
 * - 其他计划的冲突不进本计划的 map
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PlanDetailViewModel 冲突标注 (v0.4)")
class PlanDetailViewModelConflictTest {

    private val testDispatcher = StandardTestDispatcher()
    private val today = LocalDate.now()

    private val planRepo: PlanRepository = mockk(relaxed = true)
    private val taskRepo: TaskRepository = mockk(relaxed = true)
    private val completionRepo: TaskCompletionRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun task(id: String, planId: String = "p1", start: Int, end: Int) = Task(
        id = id, planId = planId, title = "任务-$id",
        startMinutes = start, endMinutes = end,
        repeatRule = RepeatRule.DAILY, orderInPlan = 0,
    )

    private fun completion(id: String, taskId: String, planId: String, start: String, end: String) =
        TaskCompletion(
            id = id, taskId = taskId, planId = planId, scheduledDate = today,
            plannedStart = LocalDateTime.parse("${today}T$start"),
            plannedEnd = LocalDateTime.parse("${today}T$end"),
            status = TaskStatus.PENDING,
        )

    private fun viewModel() = PlanDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PlanDetailViewModel.ARG_PLAN_ID to "p1")),
        planRepository = planRepo,
        taskRepository = taskRepo,
        completionRepository = completionRepo,
        conflictDetector = ConflictDetector(),
    )

    @Test
    fun `本计划两个任务重叠 — map 含双方且重叠 15 分钟`() = runTest(testDispatcher) {
        every { taskRepo.observeByPlan("p1") } returns flowOf(
            listOf(task("a", start = 9 * 60, end = 9 * 60 + 30), task("b", start = 9 * 60 + 15, end = 10 * 60))
        )
        every { completionRepo.observeByDate(any()) } returns flowOf(
            listOf(
                completion("ca", "a", "p1", "09:00", "09:30"),
                completion("cb", "b", "p1", "09:15", "10:00"),
            )
        )

        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            vm.todayConflicts.collect {}
        }
        advanceUntilIdle()

        val map = vm.todayConflicts.value
        assertEquals(setOf("a", "b"), map.keys)
        assertEquals(15, map["a"]?.overlapMinutes)
        assertEquals(15, map["b"]?.overlapMinutes)
    }

    @Test
    fun `其他计划的冲突不进本计划 map`() = runTest(testDispatcher) {
        every { taskRepo.observeByPlan("p1") } returns flowOf(
            listOf(task("a", start = 8 * 60, end = 9 * 60))
        )
        // 其他计划两个任务重叠，且与"a"不重叠
        every { completionRepo.observeByDate(any()) } returns flowOf(
            listOf(
                completion("ca", "a", "p1", "08:00", "09:00"),
                completion("cx", "x", "p2", "12:00", "13:00"),
                completion("cy", "y", "p2", "12:30", "13:30"),
            )
        )

        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            vm.todayConflicts.collect {}
        }
        advanceUntilIdle()

        assertTrue(vm.todayConflicts.value.isEmpty())
    }
}
