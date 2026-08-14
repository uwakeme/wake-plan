package com.wakeplan.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wakeplan.app.R
import kotlin.math.roundToInt

/**
 * 计划颜色 HSV 颜色盘 - ModalBottomSheet
 *
 * 布局：
 *  - 顶部 SV 方块：横向饱和度（白→透明）+ 纵向明度（透明→黑）
 *  - 下方色相条：彩虹 7 段渐变
 *  - 实时预览：当前色圆 + hex 字符串（只读）
 *  - 取消 / 确定：仅"确定"才回写 hex
 *
 * 设计取舍：
 *  - 不接任何 hex 文本输入（5.3 决策调整：去掉"自定义 hex（可选）"文本框）
 *  - DB 已存的 hex 仍然合法（PlanColorPresets.isValidHex 保留做兼容校验）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hsv by rememberHsvState(initialHex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_create_color_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            HsvColorPicker(
                hsv = hsv,
                onHsvChange = { hsv = it },
            )

            ColorPreviewRow(hsv = hsv)

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = { onConfirm(hsv.toHex()) }) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
        }
    }
}

@Immutable
private data class HsvState(val h: Float, val s: Float, val v: Float) {
    fun toHex(): String {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X", r, g, b)
    }
}

@Composable
private fun rememberHsvState(hex: String): MutableState<HsvState> =
    remember(hex) {
        val argb = android.graphics.Color.parseColor(hex)
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, arr)
        mutableStateOf(HsvState(arr[0], arr[1], arr[2]))
    }

@Composable
private fun HsvColorPicker(
    hsv: HsvState,
    onHsvChange: (HsvState) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SaturationValuePanel(
            hue = hsv.h,
            saturation = hsv.s,
            value = hsv.v,
            onChange = { s, v -> onHsvChange(hsv.copy(s = s, v = v)) },
        )
        HueSlider(
            hue = hsv.h,
            onChange = { h -> onHsvChange(hsv.copy(h = h)) },
        )
    }
}

/**
 * 饱和度/明度方块
 *  - 背景：纯色相 (S=1, V=1)
 *  - 叠加：横向白→透明 (降低饱和度) + 纵向透明→黑 (降低明度)
 *  - 选点：白色 16dp 圆 + 0.4 alpha 黑边
 *  - 触屏：awaitEachGesture 同时支持 tap 和 drag
 */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val baseColor = Color.hsv(hue, 1f, 1f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(baseColor)
            .pointerInput(hue) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onChange(
                        (down.position.x / size.width).coerceIn(0f, 1f),
                        (1f - down.position.y / size.height).coerceIn(0f, 1f),
                    )
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        onChange(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            (1f - change.position.y / size.height).coerceIn(0f, 1f),
                        )
                        change.consume()
                    }
                }
            },
    ) {
        val w = maxWidth
        val h = maxHeight

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.White,
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (w.toPx() * saturation - 8.dp.toPx()).roundToInt(),
                        y = (h.toPx() * (1f - value) - 8.dp.toPx()).roundToInt(),
                    )
                }
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
        )
    }
}

/**
 * 色相条
 *  - 7 段彩虹渐变 (0/60/120/180/240/300/360)
 *  - 白色 28dp 圆 thumb，0.3 alpha 黑边
 *  - thumb 高度 28dp 超出色相条 28dp，圆心对齐，垂直居中
 */
@Composable
private fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    val hueBrush = Brush.horizontalGradient(
        listOf(
            Color.hsv(0f, 1f, 1f),
            Color.hsv(60f, 1f, 1f),
            Color.hsv(120f, 1f, 1f),
            Color.hsv(180f, 1f, 1f),
            Color.hsv(240f, 1f, 1f),
            Color.hsv(300f, 1f, 1f),
            Color.hsv(360f, 1f, 1f),
        ),
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(hueBrush)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onChange((down.position.x / size.width * 360f).coerceIn(0f, 360f))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        onChange((change.position.x / size.width * 360f).coerceIn(0f, 360f))
                        change.consume()
                    }
                }
            },
    ) {
        val w = maxWidth
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (w.toPx() * (hue / 360f) - 14.dp.toPx()).roundToInt(),
                        y = 0,
                    )
                }
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
        )
    }
}

@Composable
private fun ColorPreviewRow(hsv: HsvState) {
    val current = Color.hsv(hsv.h, hsv.s, hsv.v)
    val hex = hsv.toHex()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(current)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Column {
            Text(
                text = stringResource(R.string.plan_create_color_picker_preview),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = hex,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
