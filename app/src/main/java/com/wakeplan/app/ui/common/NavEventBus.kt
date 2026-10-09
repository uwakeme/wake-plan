package com.wakeplan.app.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-level navigation event bus — Phase 5.6
 *
 * 用途：跨 Activity-bound 边界的导航指令
 * - TodayWidgetProvider 点击 → MainActivity 启动 → 跳 Today tab
 * - 未来 Quick Settings Tile (5.7) 类似
 *
 * 用 SharedFlow + replay=0：每次 emit 只被当前 collect 一次（新启动 Activity 收不到老的）
 * — 这正是我们想要的：每次 widget 点击触发一次 jump
 */
object NavEventBus {
    sealed interface Event {
        /** 跳到 Today tab（v0.2 widget + 5.7 Tile 都走这个） */
        data object NavToToday : Event

        /** 跳到专注页（v0.6：专注通知点击回来） */
        data class NavToFocus(val completionId: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
