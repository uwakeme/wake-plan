package com.wakeplan.app.focus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * FocusPhaseMachine 单测 — v0.6 番茄钟阶段推进
 */
@DisplayName("FocusPhaseMachine")
class FocusPhaseMachineTest {

    @Test
    fun `专注结束默认进短休息`() {
        assertEquals(
            FocusPhase.SHORT_BREAK,
            FocusPhaseMachine.nextPhase(FocusPhase.FOCUS, completedFocusRoundsAfterThis = 1, roundsBeforeLongBreak = 4),
        )
        assertEquals(
            FocusPhase.SHORT_BREAK,
            FocusPhaseMachine.nextPhase(FocusPhase.FOCUS, completedFocusRoundsAfterThis = 3, roundsBeforeLongBreak = 4),
        )
    }

    @Test
    fun `每 N 轮进长休息`() {
        assertEquals(
            FocusPhase.LONG_BREAK,
            FocusPhaseMachine.nextPhase(FocusPhase.FOCUS, completedFocusRoundsAfterThis = 4, roundsBeforeLongBreak = 4),
        )
        assertEquals(
            FocusPhase.LONG_BREAK,
            FocusPhaseMachine.nextPhase(FocusPhase.FOCUS, completedFocusRoundsAfterThis = 8, roundsBeforeLongBreak = 4),
        )
    }

    @Test
    fun `休息结束回专注（短长同）`() {
        assertEquals(
            FocusPhase.FOCUS,
            FocusPhaseMachine.nextPhase(FocusPhase.SHORT_BREAK, 1, 4),
        )
        assertEquals(
            FocusPhase.FOCUS,
            FocusPhaseMachine.nextPhase(FocusPhase.LONG_BREAK, 4, 4),
        )
    }
}
