package com.wakeplan.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 高亮任务总线 — Phase 3.3 deep link
 *
 * MainActivity 接通知点击 Intent 后调 [emit] 把 taskId 广播出来
 * TodayViewModel collect 它，TodayScreen 看到后高亮 + 滚动到可见
 * 消费后调 [clear]
 */
object HighlightBus {
    private val _highlightedTaskId = MutableStateFlow<String?>(null)
    val highlightedTaskId: StateFlow<String?> = _highlightedTaskId.asStateFlow()

    fun emit(taskId: String) {
        _highlightedTaskId.value = taskId
    }

    fun clear() {
        _highlightedTaskId.value = null
    }
}
