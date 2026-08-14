package com.wakeplan.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Wake Plan — 入场动画常量
 *
 * 来源：`.superdesign/init/theme.md` §9 Motion
 * 480ms fade-in-up + 80ms stagger，跟 design HTML `wp-animate-in` 类一致。
 */
object WpMotion {
    const val FAST = 160
    const val NORMAL = 260
    const val SLOW = 380
    const val FADE_IN_UP = 480
    const val STAGGER_MS = 80L
    const val FILL_WIDTH_SPRING = 680
}

/**
 * 错峰 fade-in-up 容器 — 列表/卡片列表入场用
 *
 * 用法：
 * ```
 * WpAnimateInContainer(index = 2) {
 *     PlanCard(...)
 * }
 * ```
 *
 * - 列表项默认用 80ms 错峰（第 0 个 0ms，第 1 个 80ms，第 2 个 160ms...）
 * - 单个 widget 不需要错峰可以用 `WpAnimateInContainer(0)`
 */
@Composable
fun WpAnimateInContainer(
    index: Int = 0,
    delayMs: Long = WpMotion.STAGGER_MS,
    durationMs: Int = WpMotion.FADE_IN_UP,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * delayMs)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = durationMs)) +
            slideInVertically(
                animationSpec = tween(durationMillis = durationMs),
                initialOffsetY = { fullHeight -> fullHeight / 6 },
            ),
    ) {
        content()
    }
}
