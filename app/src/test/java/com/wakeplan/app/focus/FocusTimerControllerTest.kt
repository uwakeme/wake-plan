package com.wakeplan.app.focus

import android.content.Context
import android.content.Intent
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.FocusSessionRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.usecase.CompleteTaskUseCase
import com.wakeplan.app.notification.StartingAlarmScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * FocusTimerController — 专注启动守门与基础行为
 *
 * Mock 所有 repository / scheduler / use case / Context，固定时钟（阶段永不走完，
 * tickLoop 空转；tearDown 用 abandon() 停场，elapsed=0 分钟不会记账）。
 * 验证：
 * - 单进行中不变量（TaskStatus KDoc「同时只允许 1 个」）在专注启动路径被守住
 * - 单场专注互斥
 * - PENDING 目标 start 的正常副作用（写 IN_PROGRESS + 取消 starting 闹钟）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("FocusTimerController 守门")
class FocusTimerControllerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC)

    private val context: Context = mockk(relaxed = true)
    private val focusSessionRepo: FocusSessionRepository = mockk(relaxed = true)
    private val taskCompletionRepo: TaskCompletionRepository = mockk(relaxed = true)
    private val completeTaskUseCase: CompleteTaskUseCase = mockk(relaxed = true)
    private val startingAlarmScheduler: StartingAlarmScheduler = mockk(relaxed = true)

    private lateinit var controller: FocusTimerController

    @BeforeEach
    fun setUp() {
        // controller 内部 new Intent(...).apply { action = ... }，JVM 单测下 android.jar 未 mock，
        // 在此拦掉 Intent 构造（setAction 保持 fluent 返回 self）
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } answers { self as Intent }

        controller = FocusTimerController(
            context = context,
            focusSessionRepository = focusSessionRepo,
            taskCompletionRepository = taskCompletionRepo,
            completeTaskUseCase = completeTaskUseCase,
            startingAlarmScheduler = startingAlarmScheduler,
            clock = fixedClock,
        )
        every { taskCompletionRepo.observeByDateRangeInclusive(any(), any()) } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        controller.abandon()
        unmockkConstructor(Intent::class)
    }

    private fun completion(
        id: String = "comp-1",
        status: TaskStatus = TaskStatus.PENDING,
    ): TaskCompletion {
        val date = LocalDate.of(2026, 8, 8)
        return TaskCompletion(
            id = id,
            taskId = "task-$id",
            planId = "plan-1",
            scheduledDate = date,
            plannedStart = LocalDateTime.of(2026, 8, 8, 9, 0),
            plannedEnd = LocalDateTime.of(2026, 8, 8, 9, 30),
            status = status,
        )
    }

    private fun task(id: String = "task-comp-1") = Task(
        id = id,
        planId = "plan-1",
        title = "任务",
        startMinutes = 540,
        endMinutes = 570,
        repeatRule = RepeatRule.DAILY,
        orderInPlan = 0,
    )

    @Test
    fun `已有别的任务进行中时 start 被守门拒绝`() = runTest {
        every { taskCompletionRepo.observeByDateRangeInclusive(any(), any()) } returns flowOf(
            listOf(completion(id = "comp-other", status = TaskStatus.IN_PROGRESS)),
        )

        val started = controller.start(completion(), task(), FocusConfig())

        assertFalse(started)
        assertNull(controller.state.value)
        coVerify(exactly = 0) { taskCompletionRepo.upsert(any()) }
        verify(exactly = 0) { context.startForegroundService(any()) }
    }

    @Test
    fun `start 自己已是进行中的任务不算冲突`() = runTest {
        val self = completion(status = TaskStatus.IN_PROGRESS)
        every { taskCompletionRepo.observeByDateRangeInclusive(any(), any()) } returns flowOf(listOf(self))

        val started = controller.start(self, task("task-comp-1"), FocusConfig())

        assertTrue(started)
        assertEquals(self.id, controller.state.value?.completionId)
        // 已是 IN_PROGRESS，不重复写状态
        coVerify(exactly = 0) { taskCompletionRepo.upsert(any()) }
    }

    @Test
    fun `已有专注场次时 start 被单场互斥拒绝`() = runTest {
        assertTrue(controller.start(completion(id = "comp-a"), task("task-comp-a"), FocusConfig()))
        assertFalse(controller.start(completion(id = "comp-b"), task("task-comp-b"), FocusConfig()))
        assertEquals("comp-a", controller.state.value?.completionId)
    }

    @Test
    fun `PENDING 目标 start 成功写 IN_PROGRESS 并按当天窗口查冲突`() = runTest {
        val c = completion()

        val started = controller.start(c, task(), FocusConfig())

        assertTrue(started)
        // 守门窗口与 TodayViewModel 点「开始」同口径：scheduledDate-1 .. scheduledDate
        verify {
            taskCompletionRepo.observeByDateRangeInclusive(
                LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 8),
            )
        }
        // PENDING → IN_PROGRESS 的写与闹钟取消在内部 scope 异步执行，轮询等待
        coVerify(timeout = 2_000) {
            taskCompletionRepo.upsert(withArg { assertEquals(TaskStatus.IN_PROGRESS, it.status) })
        }
        verify(timeout = 2_000) { startingAlarmScheduler.cancelStartingAlarm(c.id) }
    }
}
