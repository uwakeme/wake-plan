package com.wakeplan.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Wake Plan — Shapes
 * 映射自 `design/colors_and_type.css` 的 --radius-* token
 */

val WpShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // --radius-xs
    small = RoundedCornerShape(8.dp),        // --radius-sm
    medium = RoundedCornerShape(12.dp),      // --radius-md
    large = RoundedCornerShape(16.dp),       // --radius-lg
    extraLarge = RoundedCornerShape(22.dp),  // --radius-xl
)

val WpShapeXXL = RoundedCornerShape(28.dp)    // --radius-xxl
