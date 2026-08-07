package com.wakeplan.app.ui.common

import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor

/**
 * 把 "#RRGGBB" 字符串解析为 Compose Color
 * 用 framework API（AndroidColor.parseColor）跨 Compose 版本稳定 —
 * Compose 1.6+ 的 Color.value 改成了 sRGB float-encoded ULong，不再是简单 ARGB Long
 * (详见 agent memory "Compose 1.6+ Color.value 已不是 ARGB Long")
 */
fun parseHexColor(hex: String): Color = Color(AndroidColor.parseColor(hex))
