package com.wakeplan.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wake Plan — 阴影 Token
 *
 * 来源：`.superdesign/init/theme.md` §8 Shadow Tokens
 * 设计系统 v4 "Bold Edition" 用**多层叠加阴影**（不是 M3 默认的 1 层 elevation），
 * 视觉效果更立体，呼应 "bold" 设计哲学。
 *
 * 用法：
 * ```
 * Box(modifier = Modifier.wpShadow(WpShadowElevation.SM, RoundedCornerShape(24.dp)))
 * ```
 *
 * dark 模式的阴影色板单独调（黑底更黑），这里先实现 light 板，dark 后续按 `Color.isSystemDark` 切。
 */
enum class WpShadowElevation(val ambient: Dp, val spot: Dp) {
    SM(ambient = 2.dp, spot = 8.dp),
    MD(ambient = 4.dp, spot = 16.dp),
    LG(ambient = 12.dp, spot = 40.dp),
    FLOAT(ambient = 20.dp, spot = 60.dp);

    /**
     * 阴影颜色（alpha + black）
     * 跟设计系统的 4 级 shadow-sm/md/lg/float 一一对应
     */
    @Suppress("unused")
    val ambientColor: Color
        @Composable get() = Color.Black.copy(alpha = when (this) {
            SM -> 0.03f
            MD -> 0.06f
            LG -> 0.08f
            FLOAT -> 0.12f
        })

    val spotColor: Color
        @Composable get() = Color.Black.copy(alpha = when (this) {
            SM -> 0.04f
            MD -> 0.04f
            LG -> 0.04f
            FLOAT -> 0.06f
        })
}

/**
 * 给任意 shape 套一个 WpShadow 多层阴影
 *
 * 替代 M3 `Surface(tonalElevation = ...)` — 视觉更"bold"，符合 v4 设计语言。
 */
@Composable
fun Modifier.wpShadow(
    elevation: WpShadowElevation = WpShadowElevation.SM,
    shape: Shape = RoundedCornerShape(0.dp),
): Modifier = this
    .shadow(
        elevation = elevation.ambient,
        shape = shape,
        clip = false,
        ambientColor = elevation.ambientColor,
        spotColor = elevation.spotColor,
    )
    .shadow(
        elevation = elevation.spot,
        shape = shape,
        clip = false,
        ambientColor = elevation.ambientColor,
        spotColor = elevation.spotColor,
    )

/**
 * "卡" 的合集：阴影 + 边线 — 一个 modifier 调用就完事
 *
 * 视觉刷新 v0.4 的核心样式 token，全屏统一：
 * - 卡片 surface: 纯白（dark 时是 WpBgCardDark）
 * - 边线: 1px subtle 3% black / 8% white
 * - 圆角: 24dp（plan card）/ 16dp（task card）/ 12dp（segment card）
 * - 阴影: sm 默认
 *
 * 用法（一般直接用 Surface 套上即可，不一定走这个 helper）：
 * ```
 * Surface(
 *     modifier = Modifier.wpCard(),
 *     shape = RoundedCornerShape(WpRadius.xxl),
 *     color = MaterialTheme.colorScheme.surface,
 * ) { ... }
 * ```
 */
@Composable
fun Modifier.wpCard(
    elevation: WpShadowElevation = WpShadowElevation.SM,
    shape: Shape = RoundedCornerShape(WpRadius.xxl),
    border: Color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    borderWidth: Dp = 1.dp,
): Modifier = this
    .wpShadow(elevation, shape)
    .border(
        width = borderWidth,
        color = border,
        shape = shape,
    )
