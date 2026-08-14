package com.wakeplan.app.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wake Plan — 间距 Token
 *
 * 来源：`.superdesign/init/theme.md` §6 Spacing Scale
 * 中文别名 `2xs ~ 5xl`，映射到 `dp` 值。
 *
 * 业务层用 `WpSpacing.lg` 代替散写的 `16.dp`，保证全 App 间距一致。
 * 调个别值只改这里一处。
 */
object WpSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val xxxxl: Dp = 40.dp
    val xxxxxl: Dp = 56.dp
}

/**
 * 圆角 Token — 跟 `ui/theme/Shape.kt` 的 M3 Shapes 配对使用
 *
 * 卡片 / 段卡 / 大块用 lg（16dp），更柔的 sheet / bottom sheet 用 xl（22dp）。
 * 状态徽章 / 选中描边小元素用 sm（8dp）。
 */
object WpRadius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 22.dp
    val xxl: Dp = 24.dp       // PlanCard 视觉刷新用的"bold" 圆角
    val full: Dp = 9999.dp     // pill / 圆形
}
