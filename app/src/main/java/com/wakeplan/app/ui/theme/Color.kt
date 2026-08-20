package com.wakeplan.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Wake Plan — Color Tokens
 * 映射自 `design/colors_and_type.css` 的 wp 前缀令牌
 * 不要直接用这里的常量，业务层用 MaterialTheme.colorScheme.xxx
 */

// ===== LIGHT =====
val WpPrimaryLight = Color(0xFFC48520)
val WpPrimaryHoverLight = Color(0xFFA86F14)
val WpPrimaryLightSurface = Color(0xFFFCEED4) // 10% alpha blend to bg

val WpBgLight = Color(0xFFF2F0EC)
val WpBgElevatedLight = Color(0xFFF6F4EF)
val WpBgOverlayLight = Color(0xFFEAE7E2)
val WpBgCardLight = Color(0xFFFAF8F4)
val WpBgInputLight = Color(0xFFEAE7E2)

val WpTextPrimaryLight = Color(0xFF18160F)
val WpTextSecondaryLight = Color(0xFF6E695E)
val WpTextTertiaryLight = Color(0xFFA5A099)
val WpTextInverseLight = Color(0xFFFAFAF8)

val WpBorderLight = Color(0x1218100F) // 7% black on white-ish
val WpBorderStrongLight = Color(0x2418100F)
val WpBorderSubtleLight = Color(0x0818100F)

val WpSuccessLight = Color(0xFF2E9B5E)
val WpWarningLight = Color(0xFFD4901A)
val WpErrorLight = Color(0xFFC84545)
val WpInfoLight = Color(0xFF3B6FB5)

// ===== DARK =====
val WpPrimaryDark = Color(0xFFF0B44A)
val WpPrimaryHoverDark = Color(0xFFD4962E)
val WpPrimaryDarkSurface = Color(0xFF2A210F)

val WpBgDark = Color(0xFF090E1A)
val WpBgElevatedDark = Color(0xFF121A2C)
val WpBgOverlayDark = Color(0xFF0F1626)
val WpBgCardDark = Color(0xFF151E32)
val WpBgInputDark = Color(0xFF0F1626)

val WpTextPrimaryDark = Color(0xFFF0F2F8)
val WpTextSecondaryDark = Color(0xFF8492AD)
val WpTextTertiaryDark = Color(0xFF4F5E78)
val WpTextInverseDark = Color(0xFF090E1A)

val WpBorderDark = Color(0x148492AD)
val WpBorderStrongDark = Color(0x2E8492AD)
val WpBorderSubtleDark = Color(0x0A8492AD)

val WpSuccessDark = Color(0xFF5DB88A)
val WpWarningDark = Color(0xFFF0B44A)
val WpErrorDark = Color(0xFFE85C5C)
val WpInfoDark = Color(0xFF4A90D9)
