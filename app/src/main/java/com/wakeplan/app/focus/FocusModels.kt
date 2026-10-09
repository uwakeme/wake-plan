package com.wakeplan.app.focus

import java.time.Instant

/**
 * 番茄钟阶段 — v0.6 绑任务的专注页
 */
enum class FocusPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
}

/**
 * 专注计时配置 — 设置页可调，FocusTimerController.start 时读入快照
 */
data class FocusConfig(
    val focusMinutes: Int = DEFAULT_FOCUS_MINUTES,
    val shortBreakMinutes: Int = DEFAULT_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = DEFAULT_LONG_BREAK_MINUTES,
    /** 每完成 N 个专注轮进长休 */
    val roundsBeforeLongBreak: Int = DEFAULT_ROUNDS_BEFORE_LONG_BREAK,
    /** 阶段结束后自动开始下一阶段（false = 等用户点） */
    val autoStartNext: Boolean = false,
) {
    companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_SHORT_BREAK_MINUTES = 5
        const val DEFAULT_LONG_BREAK_MINUTES = 15
        const val DEFAULT_ROUNDS_BEFORE_LONG_BREAK = 4

        val ALLOWED_FOCUS_MINUTES = setOf(15, 25, 45, 60)
        val ALLOWED_SHORT_BREAK_MINUTES = setOf(3, 5, 10)
        val ALLOWED_LONG_BREAK_MINUTES = setOf(15, 30)
    }
}

/**
 * 一场专注的当前状态 — FocusTimerController 持有，专注页 / 前台 Service 共同消费
 *
 * @param phaseEndsAt 当前阶段的截止时刻（墙钟）；running=false 表示阶段已走完、
 *   等用户点"下一轮"（或 autoStartNext 时不出现这个状态）
 * @param currentRoundStartedAt 当前专注轮的开始时刻（写 focus_session 用）
 */
data class FocusState(
    val completionId: String,
    val taskId: String,
    val planId: String,
    val taskTitle: String,
    val phase: FocusPhase,
    val phaseEndsAt: Instant,
    val currentRoundStartedAt: Instant,
    val completedFocusRounds: Int,
    val running: Boolean,
    val config: FocusConfig,
) {
    /** 当前阶段的时长（分钟） */
    val phaseMinutes: Int
        get() = when (phase) {
            FocusPhase.FOCUS -> config.focusMinutes
            FocusPhase.SHORT_BREAK -> config.shortBreakMinutes
            FocusPhase.LONG_BREAK -> config.longBreakMinutes
        }

    /** 本场是否处于"专注轮刚走完、可以去打卡"的状态 */
    val isFocusPhase: Boolean get() = phase == FocusPhase.FOCUS
}
