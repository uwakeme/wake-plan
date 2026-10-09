package com.wakeplan.app.notification

import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.usecase.ScheduleManager
import com.wakeplan.app.domain.usecase.SchedulePlan
import com.wakeplan.app.domain.usecase.TaskCompletionInstantiator
import com.wakeplan.app.domain.usecase.TaskWindowCalculator
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import androidx.room.withTransaction
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * ScheduleRefresher 测试 — v0.4 从 TodayViewModel.ensureTodayCompletions 抽出的调度链
 *
 * 真实组件：TaskWindowCalculator / TaskCompletionInstantiator / ScheduleManager（纯 Kotlin 逻辑）
 * Mock 组件：3 个 Repository + StartingAlarmScheduler + UserPreferences + Clock
 */
@DisplayName("ScheduleRefresher")
class ScheduleRefresherTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val date: LocalDate = LocalDate.of(2026, 9, 11)  // 周五
    private val clock = Clock.fixed(date.atTime(4, 0).atZone(zone).toInstant(), zone)

    private val planRepository: PlanRepository = mockk()
    private val taskRepository: TaskRepository = mockk()
    private val taskCompletionRepository: TaskCompletionRepository = mockk()
    private val startingAlarmScheduler: StartingAlarmScheduler = mockk(relaxed = true)
    private val userPreferences: UserPreferences = mockk(relaxed = true) {
        every { taskStartingLeadMinutes } returns MutableStateFlow(5)
        every { taskStartingEnabled } returns MutableStateFlow(true)
    }

    private val schedulePlanSlot = slot<SchedulePlan>()

    /** 模拟 DB：upsert 会写进去，observeByDate 读的是同一份（真实 refresh 是"先铺再重读"） */
    private val completionStore = MutableStateFlow<List<TaskCompletion>>(emptyList())

    // withTransaction 静态直通（与 TemplateRepositoryImplTest 同款）
    private val db: com.wakeplan.app.data.db.WakePlanDatabase = mockk()

    init {
        io.mockk.mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    private fun refresher() = ScheduleRefresher(
        db = db,
        planRepository = planRepository,
        taskRepository = taskRepository,
        taskCompletionRepository = taskCompletionRepository,
        taskCompletionInstantiator = TaskCompletionInstantiator(TaskWindowCalculator()),
        scheduleManager = ScheduleManager(),
        startingAlarmScheduler = startingAlarmScheduler,
        userPreferences = userPreferences,
        clock = clock,
    ).also {
        every { startingAlarmScheduler.scheduleAll(capture(schedulePlanSlot)) } just Runs
    }

    private fun plan(id: String, isActive: Boolean = true, archived: Boolean = false) = Plan(
        id = id,
        title = "计划-$id",
        colorHex = "#C48520",
        startDate = date,
        isActive = isActive,
        archived = archived,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private fun task(id: String, planId: String, startMinutes: Int = 360, endMinutes: Int = 390) = Task(
        id = id,
        planId = planId,
        title = "任务-$id",
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        repeatRule = RepeatRule.DAILY,
        orderInPlan = 0,
    )

    private fun completion(id: String, taskId: String, planId: String) = TaskCompletion(
        id = id,
        taskId = taskId,
        planId = planId,
        scheduledDate = date,
        plannedStart = date.atTime(6, 0),
        plannedEnd = date.atTime(6, 30),
    )

    private fun stubData(
        plans: List<Plan>,
        tasks: List<Task>,
        completions: List<TaskCompletion>,
    ) {
        every { planRepository.observeAll() } returns MutableStateFlow(plans)
        every { taskRepository.observeAll() } returns MutableStateFlow(tasks)
        completionStore.value = completions
        every { taskCompletionRepository.observeByDate(date) } returns completionStore
        coEvery { taskCompletionRepository.upsert(any()) } coAnswers {
            completionStore.value = completionStore.value.filterNot { it.taskId == firstArg<TaskCompletion>().taskId && it.scheduledDate == firstArg<TaskCompletion>().scheduledDate } + firstArg<TaskCompletion>()
            Unit
        }
        coEvery { taskCompletionRepository.insertIfAbsent(any()) } coAnswers {
            val newOne = firstArg<TaskCompletion>()
            if (completionStore.value.none { it.taskId == newOne.taskId && it.scheduledDate == newOne.scheduledDate }) {
                completionStore.value = completionStore.value + newOne
            }
            Unit
        }
    }

    @Test
    fun `活跃 plan 的任务缺当天 completion — 补建 PENDING 实例`() = runTest {
        val p = plan("plan-1")
        val t = task("task-1", "plan-1")
        stubData(plans = listOf(p), tasks = listOf(t), completions = emptyList())

        refresher().refresh(date)

        coVerify(exactly = 1) {
            taskCompletionRepository.insertIfAbsent(match {
                it.taskId == "task-1" && it.planId == "plan-1" &&
                    it.status == TaskStatus.PENDING &&
                    it.plannedStart == date.atTime(6, 0) &&
                    it.plannedEnd == date.atTime(6, 30)
            })
        }
    }

    @Test
    fun `不活跃 plan 的任务不铺 completion`() = runTest {
        val p = plan("plan-2", isActive = false)
        val t = task("task-2", "plan-2")
        stubData(plans = listOf(p), tasks = listOf(t), completions = emptyList())

        refresher().refresh(date)

        coVerify(exactly = 0) { taskCompletionRepository.insertIfAbsent(any()) }
    }

    @Test
    fun `归档 plan 不铺 completion 也不进调度（v0_4 归档）`() = runTest {
        val p = plan("plan-arch", isActive = true, archived = true)
        val t = task("task-arch", "plan-arch")
        stubData(plans = listOf(p), tasks = listOf(t), completions = emptyList())

        refresher().refresh(date)

        coVerify(exactly = 0) { taskCompletionRepository.insertIfAbsent(any()) }
        coVerify(exactly = 1) {
            startingAlarmScheduler.scheduleAll(match { it.startingTriggers.isEmpty() })
        }
    }

    @Test
    fun `已有当天 completion 的任务不重复铺`() = runTest {
        val p = plan("plan-1")
        val t = task("task-1", "plan-1")
        val existing = completion("comp-1", "task-1", "plan-1")
        stubData(plans = listOf(p), tasks = listOf(t), completions = listOf(existing))

        refresher().refresh(date)

        coVerify(exactly = 0) { taskCompletionRepository.insertIfAbsent(any()) }
    }

    @Test
    fun `v0_4_1 refresh 不重写已有窗口 — 漂移同步移到 TaskRepository_update`() = runTest {
        val p = plan("plan-1")
        // 任务定义 6:00-7:00（时长 60），旧 completion 6:00-6:30（时长 30）。
        // refresh 不再同步窗口——否则会把链式延期落库的位移抹掉；
        // 同步职责在 TaskRepositoryImpl.update（任务定义变更时才执行）
        val t = task("task-1", "plan-1", startMinutes = 360, endMinutes = 420)
        val existing = completion("comp-1", "task-1", "plan-1")
        stubData(plans = listOf(p), tasks = listOf(t), completions = listOf(existing))

        refresher().refresh(date)

        coVerify(exactly = 0) { taskCompletionRepository.upsert(any()) }
        coVerify(exactly = 0) { taskCompletionRepository.insertIfAbsent(any()) }
    }

    @Test
    fun `scheduleAll 收到按触发时间排序的 starting triggers（plannedStart - lead）`() = runTest {
        val p = plan("plan-1")
        val early = task("task-early", "plan-1", startMinutes = 300, endMinutes = 330)  // 5:00
        val late = task("task-late", "plan-1", startMinutes = 360, endMinutes = 390)    // 6:00
        stubData(plans = listOf(p), tasks = listOf(early, late), completions = emptyList())

        refresher().refresh(date)

        val triggers = schedulePlanSlot.captured.startingTriggers
        assertEquals(2, triggers.size)
        assertEquals(date.atTime(4, 55), triggers[0].triggerAt)  // 5:00 - 5min
        assertEquals(date.atTime(5, 55), triggers[1].triggerAt)  // 6:00 - 5min
    }

    @Test
    fun `startingEnabled = false — scheduleAll 收到空 triggers`() = runTest {
        val p = plan("plan-1")
        val t = task("task-1", "plan-1")
        stubData(plans = listOf(p), tasks = listOf(t), completions = emptyList())
        every { userPreferences.taskStartingEnabled } returns MutableStateFlow(false)

        refresher().refresh(date)

        assertTrue(schedulePlanSlot.captured.startingTriggers.isEmpty())
    }
}
