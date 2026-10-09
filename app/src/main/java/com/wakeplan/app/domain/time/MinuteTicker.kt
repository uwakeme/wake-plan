package com.wakeplan.app.domain.time

import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 每分钟心跳流 — v0.4.1
 *
 * Today / PlanList / PlanDetail 这类长存 ViewModel 需要感知"现在"（时钟、跨午夜翻日）。
 * 之前各自在 VM 里 flow{delay(60s)} 内联，无法在单测里替换——虚拟时间下
 * advanceUntilIdle 会被无限 delay 循环挂死。抽成可注入组件后测试传有限流即可。
 */
@Singleton
class MinuteTicker @Inject constructor(
    private val clock: Clock,
) {
    val now: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(TICK_INTERVAL_MS)
        }
    }

    companion object {
        const val TICK_INTERVAL_MS = 60_000L
    }
}
