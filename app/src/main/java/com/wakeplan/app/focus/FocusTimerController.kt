package com.wakeplan.app.focus

import android.content.Context
import android.content.Intent
import com.wakeplan.app.domain.model.FocusOutcome
import com.wakeplan.app.domain.model.FocusSession
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.FocusSessionRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.newId
import com.wakeplan.app.domain.usecase.CompleteTaskUseCase
import com.wakeplan.app.notification.StartingAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 专注计时控制器 — v0.6 番茄钟核心
 *
 * 单例持有 [FocusState]（专注页 / 前台 Service 共同消费），自带 scope 每秒心跳：
 * - 阶段走完：FOCUS → 写 focus_session(FINISHED) → 进休息（长/短由轮数决定）；
 *   BREAK → 等待下一轮（autoStartNext 时自动开）
 * - 一场专注绑定一个 TaskCompletion；专注期间任务保持 IN_PROGRESS
 * - 打卡（completeTask）：走 [CompleteTaskUseCase]（与 Today 手动完成同一条链：
 *   级联落库 + 闹钟重排 + 完成通知），当前专注轮按实际时长记账后整场结束
 *
 * 纯逻辑部分抽在 [FocusPhaseMachine]（可单测）；本类只做状态搬运与 IO。
 */
@Singleton
class FocusTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusSessionRepository: FocusSessionRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val startingAlarmScheduler: StartingAlarmScheduler,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<FocusState?>(null)
    val state: StateFlow<FocusState?> = _state.asStateFlow()

    /** 开始一场专注（同一时间只允许一场；已在跑的直接忽略） */
    fun start(
        completion: TaskCompletion,
        task: Task,
        config: FocusConfig,
    ) {
        if (_state.value != null) return
        val now = Instant.now(clock)
        _state.value = FocusState(
            completionId = completion.id,
            taskId = task.id,
            planId = completion.planId,
            taskTitle = task.title,
            phase = FocusPhase.FOCUS,
            phaseEndsAt = now.plusSeconds(config.focusMinutes * 60L),
            currentRoundStartedAt = now,
            completedFocusRounds = 0,
            running = true,
            config = config,
        )
        if (completion.status == TaskStatus.PENDING) {
            scope.launch {
                // 与 Today 点"开始"同语义：写 actualStart + 取消 starting 闹钟
                taskCompletionRepository.upsert(
                    completion.copy(status = TaskStatus.IN_PROGRESS, actualStart = java.time.LocalDateTime.now(clock)),
                )
                startingAlarmScheduler.cancelStartingAlarm(completion.id)
            }
        }
        context.startForegroundService(
            Intent(context, FocusTimerService::class.java).apply { action = FocusTimerService.ACTION_START },
        )
        tickLoop()
    }

    /** 用户点"下一轮"（running=false 等待时；休息结束等待 → 开下一专注轮） */
    fun advance() {
        val s = _state.value ?: return
        if (s.running) return
        transitionTo(s.copy(phase = FocusPhase.FOCUS), autoStart = true)
    }

    /**
     * 跳过当前阶段：休息跳过直接进下一轮；专注跳过按 ABANDONED 记账进休息
     * （放弃的轮不计入长休进度）
     */
    fun skipPhase() {
        val s = _state.value ?: return
        if (!s.running) {
            advance()
            return
        }
        val next = when (s.phase) {
            FocusPhase.FOCUS -> {
                recordRound(s, FocusOutcome.ABANDONED)
                s.copy(phase = FocusPhaseMachine.nextPhase(
                    current = s.phase,
                    completedFocusRoundsAfterThis = s.completedFocusRounds, // 不计入
                    roundsBeforeLongBreak = s.config.roundsBeforeLongBreak,
                ))
            }
            FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK ->
                s.copy(phase = FocusPhase.FOCUS)
        }
        transitionTo(next, autoStart = true)
    }

    /**
     * 完成打卡：任务标记完成（CompleteTaskUseCase 全链），当前专注轮按实际
     * 时长记账（走满=FINISHED，提前=ABANDONED），整场结束并停前台 Service
     */
    fun completeTask() {
        val s = _state.value ?: return
        scope.launch {
            if (s.isFocusPhase) {
                val elapsedMin = Duration.between(s.currentRoundStartedAt, Instant.now(clock))
                    .toMinutes().toInt()
                val outcome = if (elapsedMin >= s.config.focusMinutes) FocusOutcome.FINISHED else FocusOutcome.ABANDONED
                recordRoundInternal(s, outcome)
            }
            completeTaskUseCase(s.completionId)
            stop()
        }
    }

    /** 放弃整场：当前专注轮 >=1 分钟按 ABANDONED 记账，任务保持 IN_PROGRESS */
    fun abandon() {
        val s = _state.value ?: return
        if (s.isFocusPhase) {
            val elapsedMin = Duration.between(s.currentRoundStartedAt, Instant.now(clock)).toMinutes().toInt()
            if (elapsedMin >= MIN_RECORDED_MINUTES) {
                recordRound(s, FocusOutcome.ABANDONED)
            }
        }
        stop()
    }

    private fun stop() {
        _state.value = null
        context.stopService(Intent(context, FocusTimerService::class.java))
    }

    // ---- 内部 ----

    private fun tickLoop() {
        scope.launch {
            while (_state.value != null) {
                delay(TICK_MS)
                val s = _state.value ?: break
                if (s.running && !Instant.now(clock).isBefore(s.phaseEndsAt)) {
                    onPhaseFinished(s)
                }
            }
        }
    }

    private fun onPhaseFinished(s: FocusState) {
        when (s.phase) {
            FocusPhase.FOCUS -> {
                recordRound(s, FocusOutcome.FINISHED)
                val nextRounds = s.completedFocusRounds + 1
                transitionTo(
                    s.copy(
                        phase = FocusPhaseMachine.nextPhase(
                            current = s.phase,
                            completedFocusRoundsAfterThis = nextRounds,
                            roundsBeforeLongBreak = s.config.roundsBeforeLongBreak,
                        ),
                        completedFocusRounds = nextRounds,
                    ),
                    autoStart = s.config.autoStartNext,
                )
            }
            FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> {
                transitionTo(
                    s.copy(phase = FocusPhase.FOCUS),
                    autoStart = s.config.autoStartNext,
                )
            }
        }
    }

    /** 进入 next 的新阶段：重算 phaseEndsAt 与轮起点 */
    private fun transitionTo(next: FocusState, autoStart: Boolean) {
        val now = Instant.now(clock)
        _state.value = next.copy(
            phaseEndsAt = now.plusSeconds(next.phaseMinutes * 60L),
            currentRoundStartedAt = if (next.phase == FocusPhase.FOCUS) now else next.currentRoundStartedAt,
            running = autoStart,
        )
    }

    private fun recordRound(s: FocusState, outcome: FocusOutcome) {
        val now = Instant.now(clock)
        val session = FocusSession(
            id = newId(),
            completionId = s.completionId,
            planId = s.planId,
            startedAt = s.currentRoundStartedAt,
            endedAt = now,
            plannedMinutes = s.config.focusMinutes,
            actualMinutes = Duration.between(s.currentRoundStartedAt, now).toMinutes().toInt()
                .coerceAtLeast(0),
            outcome = outcome,
        )
        scope.launch { focusSessionRepository.insert(session) }
    }

    private suspend fun recordRoundInternal(s: FocusState, outcome: FocusOutcome) {
        val now = Instant.now(clock)
        focusSessionRepository.insert(
            FocusSession(
                id = newId(),
                completionId = s.completionId,
                planId = s.planId,
                startedAt = s.currentRoundStartedAt,
                endedAt = now,
                plannedMinutes = s.config.focusMinutes,
                actualMinutes = Duration.between(s.currentRoundStartedAt, now).toMinutes().toInt()
                    .coerceAtLeast(0),
                outcome = outcome,
            ),
        )
    }

    companion object {
        private const val TICK_MS = 1_000L

        /** 放弃时少于这个分钟数不记账（误触保护） */
        const val MIN_RECORDED_MINUTES = 1
    }
}

/**
 * 阶段推进纯函数 — 可单测
 */
object FocusPhaseMachine {
    /** 专注轮结束后进哪种休息；休息结束后回专注 */
    fun nextPhase(
        current: FocusPhase,
        completedFocusRoundsAfterThis: Int,
        roundsBeforeLongBreak: Int,
    ): FocusPhase = when (current) {
        FocusPhase.FOCUS ->
            if (completedFocusRoundsAfterThis > 0 && completedFocusRoundsAfterThis % roundsBeforeLongBreak == 0) {
                FocusPhase.LONG_BREAK
            } else {
                FocusPhase.SHORT_BREAK
            }
        FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> FocusPhase.FOCUS
    }
}
