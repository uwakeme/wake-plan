package com.wakeplan.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Wake Plan — Typography
 * 映射自 `design/colors_and_type.css` 的 --text-* token
 * 字体族：
 *   - 拉丁文：DM Sans（v0.1 阶段不打包字体文件，先用 FontFamily.Default，UI 上仍按 token 走）
 *   - 中文：系统 Noto Sans SC（Android 系统自带，无需打包）
 *   - 数字 / 时间：JetBrains Mono（v0.1 阶段先用 FontFamily.Monospace）
 */

private val displayFamily = FontFamily.Default
private val bodyFamily = FontFamily.Default
private val monoFamily = FontFamily.Monospace

private val tightLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val WpTypography = Typography(
    // display: 60sp/66sp Bold -0.04em — 用于启动屏 / 大数字
    displayLarge = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 60.sp,
        lineHeight = 66.sp,
        letterSpacing = (-0.04f * 60).sp,
        lineHeightStyle = tightLineHeightStyle,
    ),
    // 3xl: 36sp/44sp Bold
    headlineLarge = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.04f * 36).sp,
    ),
    // 2xl: 28sp/36sp Semibold
    headlineMedium = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.04f * 28).sp,
    ),
    // xl: 20sp/28sp Semibold — 卡片标题
    titleLarge = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    // lg: 17sp/24sp Medium — 列表项主文案
    titleMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    // base: 15sp/22sp Normal — 正文
    bodyLarge = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    // sm: 13sp/18sp Normal — 副文案
    bodyMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // xs: 11sp/16sp Medium 0.06em — tab 标签 / 角标
    labelMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.66.sp, // 0.06em
    ),
    // 2xs: 10sp/14sp Medium 0.06em — 最小标签
    labelSmall = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp, // 0.06em
    ),
)

/** 时间 / 倒计时专用字体族：等宽数字 */
val WpMonoStyle = TextStyle(
    fontFamily = monoFamily,
    fontWeight = FontWeight.Medium,
)
