package com.wakeplan.app.notification

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * RecapScheduler 纯函数测试 — v0.5 每日回顾
 *
 * 调度对齐 21:00（PRD 定值）：今天 21 点前 → 到今天 21:00；
 * 21 点及以后（含恰好）→ 到明天 21:00，避免边界重复触发。
 */
@DisplayName("RecapScheduler.delayUntilNextRecap")
class RecapSchedulerTest {

    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun `21 点前 — 延迟到当天 21 点`() {
        val now = today.atTime(8, 30)
        assertEquals(Duration.ofHours(12).plusMinutes(30), RecapScheduler.delayUntilNextRecap(now))
    }

    @Test
    fun `21 点后 — 延迟到明天 21 点`() {
        val now = today.atTime(23, 15)
        assertEquals(Duration.ofHours(21).plusMinutes(45), RecapScheduler.delayUntilNextRecap(now))
    }

    @Test
    fun `恰好 21 点整 — 排到明天（避免边界重复触发）`() {
        val now = today.atTime(21, 0)
        assertEquals(Duration.ofHours(24), RecapScheduler.delayUntilNextRecap(now))
    }

    @Test
    fun `午夜前 — 跨到当天 21 点（仍是今天）`() {
        val now = today.atTime(0, 0)
        assertEquals(Duration.ofHours(21), RecapScheduler.delayUntilNextRecap(now))
    }
}
