package com.wakeplan.app.ui.plan

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * PlanListViewModel 归档拆分测试 — v0.4
 *
 * - plansWithStats 只出未归档计划；archivedPlans 只出已归档
 * - setArchived(true) 写回 repository（copy archived 字段）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PlanListViewModel 归档 (v0.4)")
class PlanListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val planRepo: PlanRepository = mockk(relaxed = true)
    private val taskRepo: TaskRepository = mockk(relaxed = true)
    private val taskCompletionRepo: TaskCompletionRepository = mockk(relaxed = true)

    private val plansFlow = MutableStateFlow(listOf(activePlan(), archivedPlan()))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { planRepo.observeAll() } returns plansFlow
        every { taskRepo.observeAll() } returns MutableStateFlow(emptyList())
        every { taskCompletionRepo.observeByDate(any()) } returns MutableStateFlow(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun activePlan() = Plan(
        id = "plan-active",
        title = "晨练",
        colorHex = "#C48520",
        startDate = LocalDate.of(2026, 9, 1),
        isActive = true,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private fun archivedPlan() = Plan(
        id = "plan-archived",
        title = "旧计划",
        colorHex = "#3B6FB5",
        startDate = LocalDate.of(2026, 8, 1),
        isActive = true,
        archived = true,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-20T00:00:00Z"),
    )

    private fun viewModel() = PlanListViewModel(planRepo, taskRepo, taskCompletionRepo)

    @Test
    fun `plansWithStats 只含未归档，archivedPlans 只含已归档`() = runTest(testDispatcher) {
        val vm = viewModel()
        // WhileSubscribed 的 stateIn 需要有订阅者才更新 value
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            vm.plansWithStats.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            vm.archivedPlans.collect {}
        }
        advanceUntilIdle()

        assertEquals(listOf("plan-active"), vm.plansWithStats.value.map { it.plan.id })
        assertEquals(listOf("plan-archived"), vm.archivedPlans.value.map { it.plan.id })
    }

    @Test
    fun `setArchived true — 用 copy(archived=true) 调 update`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        coEvery { planRepo.getById("plan-active") } returns activePlan()
        vm.setArchived("plan-active", archived = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            planRepo.update(match { it.id == "plan-active" && it.archived })
        }
    }

    @Test
    fun `setArchived false — 写回 archived=false`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        coEvery { planRepo.getById("plan-archived") } returns archivedPlan()
        vm.setArchived("plan-archived", archived = false)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            planRepo.update(match { it.id == "plan-archived" && !it.archived })
        }
    }
}
