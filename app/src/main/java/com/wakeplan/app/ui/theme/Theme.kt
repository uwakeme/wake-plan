package com.wakeplan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Wake Plan — Material 3 Theme
 * 严格映射 `design/colors_and_type.css` 的 wp 令牌
 *
 * 使用：
 * ```kotlin
 * WakePlanTheme {
 *   Surface { Text("hi", color = MaterialTheme.colorScheme.primary) }
 * }
 * ```
 */

private val LightColors = lightColorScheme(
    primary = WpPrimaryLight,
    onPrimary = WpTextInverseLight,
    primaryContainer = WpPrimaryLightSurface,
    onPrimaryContainer = WpPrimaryLight,

    secondary = WpTextSecondaryLight,
    onSecondary = WpTextInverseLight,

    tertiary = WpInfoLight,
    onTertiary = WpTextInverseLight,

    background = WpBgLight,
    onBackground = WpTextPrimaryLight,

    surface = WpBgCardLight,
    onSurface = WpTextPrimaryLight,
    surfaceVariant = WpBgOverlayLight,
    onSurfaceVariant = WpTextSecondaryLight,

    surfaceContainerLowest = WpBgCardLight,
    surfaceContainerLow = WpBgElevatedLight,
    surfaceContainer = WpBgOverlayLight,
    surfaceContainerHigh = WpBgInputLight,
    surfaceContainerHighest = WpBgInputLight,

    outline = WpBorderStrongLight,
    outlineVariant = WpBorderLight,

    error = WpErrorLight,
    onError = WpTextInverseLight,
)

private val DarkColors = darkColorScheme(
    primary = WpPrimaryDark,
    onPrimary = WpTextInverseDark,
    primaryContainer = WpPrimaryDarkSurface,
    onPrimaryContainer = WpPrimaryDark,

    secondary = WpTextSecondaryDark,
    onSecondary = WpTextInverseDark,

    tertiary = WpTextTertiaryDark,
    onTertiary = WpTextPrimaryDark,

    background = WpBgDark,
    onBackground = WpTextPrimaryDark,

    surface = WpBgCardDark,
    onSurface = WpTextPrimaryDark,
    surfaceVariant = WpBgOverlayDark,
    onSurfaceVariant = WpTextSecondaryDark,

    surfaceContainerLowest = WpBgDark,
    surfaceContainerLow = WpBgElevatedDark,
    surfaceContainer = WpBgOverlayDark,
    surfaceContainerHigh = WpBgInputDark,
    surfaceContainerHighest = WpBgInputDark,

    outline = WpBorderStrongDark,
    outlineVariant = WpBorderDark,

    error = WpErrorDark,
    onError = WpTextInverseDark,
)

@Composable
fun WakePlanTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    // 强制深色主题 — 始终使用DarkColors
    val colorScheme = DarkColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WpTypography,
        shapes = WpShapes,
        content = content,
    )
}
