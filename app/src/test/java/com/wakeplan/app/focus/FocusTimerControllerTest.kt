package com.wakeplan.app.focus

import android.content.Context
import android.content.Intent
import com.wakeplan.app.domain.model.FocusOutcome
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
import java.time.ZoneId
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
 * FocusTimerController — 专注启动守门、轮次记账与阶段流转
 *
 * Mock 所有 repository / scheduler / use case / Context；时钟可手动推进
 * （[MutableClock]），tickLoop 按真实 1s 心跳轮询，用 [awaitPhase] 等阶段推进；
 * tearDown 用 abandon() 停场。验证：
 * - 单进行中不变量（TaskStatus KDoc「同时只允许 1 个」）在专注启动路径被守住
 * - 单场专注互斥
 * - PENDING 目标 start 的正常副作用（写 IN_PROGRESS + 取消 starting 闹钟）
 * - 轮次记账：走完=FINISHED / 跳过与提前打卡=ABANDONED / 放弃的 1 分钟误触保护
 * - abandon 的 KDoc 契约：任务保持 IN_PROGRESS，由用户在 Today 收尾
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("FocusTimerController 守门与记账")
class FocusTimerControllerTest {

    private val mutableClock = MutableClock(Instant.parse("2026-08-08T12:00:00Z"))

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
            clock = mutableClock,
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

    // ---- 轮次记账与阶段流转 ----

    @Test
    fun `专注轮走完按 FINISHED 记账并进短休`() = runTest {
        val c = completion()
        controller.start(c, task(), FocusConfig(focusMinutes = 25))
        mutableClock.now = mutableClock.now.plusSeconds(25 * 60L)

        val s = awaitPhase(FocusPhase.SHORT_BREAK)

        assertEquals(1, s?.completedFocusRounds)
        coVerify(timeout = 2_000, exactly = 1) {
            focusSessionRepo.insert(withArg {
                assertEquals(FocusOutcome.FINISHED, it.outcome)
                assertEquals(25, it.actualMinutes)
                assertEquals(c.id, it.completionId)
            })
        }
    }

    @Test
    fun `每 N 轮走完进长休`() = runTest {
        controller.start(
            completion(id = "comp-lb"),
            task("task-comp-lb"),
            FocusConfig(focusMinutes = 1, shortBreakMinutes = 1, roundsBeforeLongBreak = 2),
        )
        mutableClock.now = mutableClock.now.plusSeconds(60)
        // autoStartNext=false：第 1 轮走完进短休即停表，等用户点「下一阶段」
        val shortBreak = awaitPhase(FocusPhase.SHORT_BREAK)
        assertEquals(false, shortBreak?.running)
        controller.advance()                         // 第 2 轮开跑
        mutableClock.now = mutableClock.now.plusSeconds(60)

        val longBreak = awaitPhase(FocusPhase.LONG_BREAK)

        assertEquals(2, longBreak?.completedFocusRounds)
        coVerify(timeout = 2_000, exactly = 2) {
            focusSessionRepo.insert(withArg { assertEquals(FocusOutcome.FINISHED, it.outcome) })
        }
    }

    @Test
    fun `跳过专注阶段按 ABANDONED 记账且不计长休进度`() = runTest {
        controller.start(completion(), task(), FocusConfig(focusMinutes = 25, roundsBeforeLongBreak = 4))
        mutableClock.now = mutableClock.now.plusSeconds(10 * 60L)

        controller.skipPhase()

        val s = awaitPhase(FocusPhase.SHORT_BREAK)
        assertEquals(0, s?.completedFocusRounds)
        coVerify(timeout = 2_000, exactly = 1) {
            focusSessionRepo.insert(withArg {
                assertEquals(FocusOutcome.ABANDONED, it.outcome)
                assertEquals(10, it.actualMinutes)
                assertEquals(25, it.plannedMinutes)
            })
        }
    }

    @Test
    fun `放弃整场满 1 分钟按 ABANDONED 记账且任务保持进行中`() = runTest {
        // 场景用已 IN_PROGRESS 的任务，排除 start 自身 upsert 干扰契约断言
        val c = completion(status = TaskStatus.IN_PROGRESS)
        controller.start(c, task("task-comp-1"), FocusConfig(focusMinutes = 25))
        mutableClock.now = mutableClock.now.plusSeconds(2 * 60L)

        controller.abandon()

        coVerify(timeout = 2_000, exactly = 1) {
            focusSessionRepo.insert(withArg {
                assertEquals(FocusOutcome.ABANDONED, it.outcome)
                assertEquals(2, it.actualMinutes)
            })
        }
        // KDoc 契约：放弃整场任务保持 IN_PROGRESS，由用户在 Today 收尾
        coVerify(exactly = 0) { taskCompletionRepo.upsert(any()) }
        assertNull(controller.state.value)
    }

    @Test
    fun `放弃整场不足 1 分钟不记账`() = runTest {
        controller.start(
            completion(status = TaskStatus.IN_PROGRESS),
            task(),
            FocusConfig(focusMinutes = 25),
        )

        controller.abandon()

        // 误触保护：elapsed=0 < MIN_RECORDED_MINUTES
        coVerify(exactly = 0) { focusSessionRepo.insert(any()) }
        assertNull(controller.state.value)
    }

    @Test
    fun `完成打卡提前结束按 ABANDONED 记账并走完成链`() = runTest {
        val c = completion()
        controller.start(c, task(), FocusConfig(focusMinutes = 25))
        mutableClock.now = mutableClock.now.plusSeconds(10 * 60L)

        controller.completeTask()

        coVerify(timeout = 2_000, exactly = 1) {
            focusSessionRepo.insert(withArg {
                assertEquals(FocusOutcome.ABANDONED, it.outcome)
                assertEquals(10, it.actualMinutes)
            })
        }
        coVerify(timeout = 2_000) { completeTaskUseCase.invoke(c.id) }
        awaitStopped()
    }

    @Test
    fun `完成打卡走满整轮按 FINISHED 记账`() = runTest {
        val c = completion()
        controller.start(c, task(), FocusConfig(focusMinutes = 25))
        mutableClock.now = mutableClock.now.plusSeconds(25 * 60L)

        controller.completeTask()

        coVerify(timeout = 2_000, exactly = 1) {
            focusSessionRepo.insert(withArg {
                assertEquals(FocusOutcome.FINISHED, it.outcome)
                assertEquals(25, it.actualMinutes)
            })
        }
        awaitStopped()
    }

    /** tickLoop 以真实 1s 心跳轮询，推进时钟后用这个等阶段推进到位 */
    private fun awaitPhase(phase: FocusPhase, timeoutMs: Long = 5_000): FocusState? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = controller.state.value
            if (s?.phase == phase) return s
            Thread.sleep(50)
        }
        val s = controller.state.value
        assertEquals(phase, s?.phase, "等待阶段推进到 $phase 超时，当前=${s?.phase}")
        return s
    }

    /**
     * completeTask 的落库与 stop() 在内部 scope 异步执行；等 state 清空再进 tearDown，
     * 否则 stop() 里的 Intent 构造会撞上已拆除的构造 mock（MockKException 泄漏到下一条用例）
     *
     * 控制器已保证 Intent 在 state 清空之前构造好，这里再等 stopService 落地，
     * 让「后台彻底收工」成为确定的事实而不是靠时序碰运气。
     */
    private fun awaitStopped(timeoutMs: Long = 2_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && controller.state.value != null) {
            Thread.sleep(20)
        }
        assertNull(controller.state.value, "等待专注场结束超时")
        verify(timeout = 1_000) { context.stopService(any()) }
    }
}

/** 可手动推进的测试时钟 — tickLoop 按墙钟判断阶段走完 */
private class MutableClock(initial: Instant) : Clock() {
    var now: Instant = initial
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}
