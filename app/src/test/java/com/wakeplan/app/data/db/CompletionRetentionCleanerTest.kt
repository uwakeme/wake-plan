package com.wakeplan.app.data.db

import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.prefs.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * CompletionRetentionCleaner 单测 — v0.5 阶段六
 *
 * 覆盖：默认 0 不删 / 90 天与 1 年的 cutoff 计算 / DAO 只按 cutoff 调用
 */
@DisplayName("CompletionRetentionCleaner")
class CompletionRetentionCleanerTest {

    private val today = LocalDate.of(2026, 10, 9)
    private val clock = Clock.fixed(
        today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
    )
    private val dao: TaskCompletionDao = mockk(relaxed = true) {
        coEvery { deleteTerminalOlderThan(any()) } returns 3
    }
    private val preferences: UserPreferences = mockk()

    private fun cleaner(retentionDays: Int) = CompletionRetentionCleaner(
        preferences = mockk {
            every { completionRetentionDays } returns flowOf(retentionDays)
        },
        completionDao = dao,
        clock = clock,
    )

    @Test
    fun `默认 0 永久 — 不触发任何删除`() = runTest {
        val cleaned = cleaner(0).cleanIfConfigured()
        assertEquals(0, cleaned)
        coVerify(exactly = 0) { dao.deleteTerminalOlderThan(any()) }
    }

    @Test
    fun `90 天 — cutoff 是今天减 90`() = runTest {
        cleaner(90).cleanIfConfigured()
        coVerify(exactly = 1) { dao.deleteTerminalOlderThan(today.minusDays(90)) }
    }

    @Test
    fun `365 天 — cutoff 是今天减 365`() = runTest {
        cleaner(365).cleanIfConfigured()
        coVerify(exactly = 1) { dao.deleteTerminalOlderThan(today.minusDays(365)) }
    }

    @Test
    fun `cutoffFor 纯函数 — 跨年正确（2025-10-09 + 365 天 = 2026-10-09，区间无闰日）`() {
        val cleaner = CompletionRetentionCleaner(preferences, dao, clock)
        assertEquals(LocalDate.of(2025, 10, 9), cleaner.cutoffFor(365))
    }
}
