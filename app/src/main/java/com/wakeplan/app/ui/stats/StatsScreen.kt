package com.wakeplan.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import com.wakeplan.app.R
import com.wakeplan.app.domain.usecase.StatsUseCase
import com.wakeplan.app.ui.common.WpRadius
import com.wakeplan.app.ui.common.parseHexColor
import com.wakeplan.app.ui.theme.PlanSideProjectDark
import com.wakeplan.app.ui.theme.WpInfoDark
import com.wakeplan.app.ui.theme.WpSuccessDark
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun StatsScreen() {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(44.dp))

        Text(
            text = stringResource(R.string.stats_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.04).sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(16.dp))

        RangeToggle(
            current = state.range,
            onChange = { viewModel.selectRange(it) },
        )

        Spacer(Modifier.height(16.dp))

        val result = state.result
        if (result == null) {
            EmptyStatsState()
        } else {
            StatsGrid(result)
            Spacer(Modifier.height(16.dp))
            PerDayLineChart(result)
            Spacer(Modifier.height(16.dp))
            PerPlanList(result)
            Spacer(Modifier.height(16.dp))
            PerPlanBarChart(result)
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun RangeToggle(
    current: StatsUseCase.Range,
    onChange: (StatsUseCase.Range) -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(WpRadius.xl))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(WpRadius.xl),
            )
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RangeButton(
                label = stringResource(R.string.stats_range_week),
                selected = current == StatsUseCase.Range.LAST_7_DAYS,
                onClick = { onChange(StatsUseCase.Range.LAST_7_DAYS) },
                modifier = Modifier.weight(1f),
            )
            RangeButton(
                label = stringResource(R.string.stats_range_month),
                selected = current == StatsUseCase.Range.LAST_30_DAYS,
                onClick = { onChange(StatsUseCase.Range.LAST_30_DAYS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RangeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(WpRadius.lg),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color(0xFF090E1A) else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun StatsGrid(result: StatsUseCase.StatsResult) {
    val totalTasks = result.totalCompleted + result.totalSkipped + result.totalPending
    val focusRate = (result.onTimeRate * 100).toInt()
    val streakDays = calculateStreak(result.perDay)

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                icon = Icons.Outlined.Schedule,
                value = totalTasks.toString(),
                label = stringResource(R.string.stats_metric_total_duration),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.CheckCircle,
                value = result.totalCompleted.toString(),
                label = stringResource(R.string.stats_metric_tasks_done),
                color = WpInfoDark,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                icon = Icons.Outlined.StarOutline,
                value = "$focusRate%",
                label = stringResource(R.string.stats_metric_focus),
                color = WpSuccessDark,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Outlined.LocalFireDepartment,
                value = stringResource(R.string.stats_streak_days, streakDays),
                label = stringResource(R.string.stats_metric_streak),
                color = PlanSideProjectDark,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun calculateStreak(perDay: List<StatsUseCase.DayBucket>): Int {
    var streak = 0
    for (bucket in perDay.asReversed()) {
        if (bucket.completedCount > 0) {
            streak++
        } else {
            break
        }
    }
    return streak
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 28.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun PerPlanList(result: StatsUseCase.StatsResult) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_per_plan_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            if (result.perPlan.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_per_plan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                result.perPlan.forEach { bucket ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    runCatching { parseHexColor(bucket.colorHex) }
                                        .getOrDefault(MaterialTheme.colorScheme.primary)
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = bucket.planTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${bucket.completedCount}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStatsState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.stats_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.stats_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PerDayLineChart(result: StatsUseCase.StatsResult) {
    val perDay = result.perDay
    if (perDay.isEmpty() || perDay.all { it.completedCount == 0 }) {
        Text(
            text = stringResource(R.string.stats_per_day_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
    val maxY = max(1, perDay.maxOf { it.completedCount })
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = onSurfaceVariant,
    )
    val density = LocalDensity.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_per_day_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            if (perDay.all { it.completedCount == 0 }) {
                Text(
                    text = stringResource(R.string.stats_per_day_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val padding = with(density) { 24.dp.toPx() }
                    val plotW = w - padding
                    val plotH = h - with(density) { 18.dp.toPx() }
                    val plotTop = with(density) { 4.dp.toPx() }
                    val plotLeft = padding

                    for (i in 0..2) {
                        val y = plotTop + plotH * (1f - i / 2f)
                        val label = ((maxY.toFloat() * i / 2f).toInt()).toString()
                        if (i == 1 && (label == "0" || label == maxY.toString())) continue
                        drawLine(
                            color = outlineColor.copy(alpha = 0.4f),
                            start = Offset(plotLeft, y),
                            end = Offset(plotLeft + plotW, y),
                            strokeWidth = 1f,
                        )
                        val text = textMeasurer.measure(label, style = labelStyle)
                        drawText(
                            textLayoutResult = text,
                            topLeft = Offset(
                                x = plotLeft - text.size.width - 4f,
                                y = y - text.size.height / 2f,
                            ),
                        )
                    }

                    if (perDay.size < 2) return@Canvas

                    val xStep = plotW / (perDay.size - 1).toFloat()
                    val points = perDay.mapIndexed { idx, bucket ->
                        Offset(
                            x = plotLeft + idx * xStep,
                            y = plotTop + plotH * (1f - bucket.completedCount.toFloat() / maxY),
                        )
                    }
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                    )
                    points.forEach { p ->
                        drawCircle(
                            color = primaryColor,
                            radius = with(density) { 3.dp.toPx() },
                            center = p,
                        )
                    }

                    val xLabelStyle = labelStyle
                    val xLabelIndices = listOf(0, perDay.size / 2, perDay.size - 1).distinct()
                    xLabelIndices.forEach { idx ->
                        val date = perDay[idx].date
                        val dateStr = date.format(X_LABEL_FMT)
                        val text = textMeasurer.measure(dateStr, style = xLabelStyle)
                        drawText(
                            textLayoutResult = text,
                            topLeft = Offset(
                                x = points[idx].x - text.size.width / 2f,
                                y = plotTop + plotH + 4f,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private val X_LABEL_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@Composable
private fun PerPlanBarChart(result: StatsUseCase.StatsResult) {
    val perPlan = result.perPlan
    if (perPlan.isEmpty()) {
        Text(
            text = stringResource(R.string.stats_per_plan_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
    val maxY = max(1, perPlan.maxOf { it.completedCount })
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
    val density = LocalDensity.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_per_plan_bar_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            if (perPlan.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_per_day_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val paddingBottom = with(density) { 28.dp.toPx() }
                    val paddingLeft = with(density) { 24.dp.toPx() }
                    val plotW = w - paddingLeft
                    val plotH = h - paddingBottom
                    val plotTop = with(density) { 4.dp.toPx() }
                    val plotLeft = paddingLeft

                    drawLine(
                        color = outlineColor,
                        start = Offset(plotLeft, plotTop + plotH),
                        end = Offset(plotLeft + plotW, plotTop + plotH),
                        strokeWidth = 1f,
                    )

                    for (i in 0..2) {
                        val yVal = (maxY.toFloat() * i / 2f).toInt()
                        val y = plotTop + plotH * (1f - i / 2f)
                        if (i == 1 && (yVal == 0 || yVal == maxY)) continue
                        val text = textMeasurer.measure(yVal.toString(), style = labelStyle)
                        drawText(
                            textLayoutResult = text,
                            topLeft = Offset(
                                x = plotLeft - text.size.width - 4f,
                                y = y - text.size.height / 2f,
                            ),
                        )
                    }

                    val barCount = perPlan.size
                    val groupWidth = plotW / barCount
                    val barWidth = groupWidth * 0.6f
                    perPlan.forEachIndexed { idx, bucket ->
                        val centerX = plotLeft + groupWidth * idx + groupWidth / 2f
                        val barLeft = centerX - barWidth / 2f
                        val barHeight = plotH * bucket.completedCount.toFloat() / maxY
                        val barTop = plotTop + plotH - barHeight
                        val color = runCatching { parseHexColor(bucket.colorHex) }
                            .getOrDefault(Color.Gray)
                        drawRect(
                            color = color,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                        )
                        val valueText = textMeasurer.measure(
                            bucket.completedCount.toString(),
                            style = labelStyle.copy(fontSize = 10.sp),
                        )
                        drawText(
                            textLayoutResult = valueText,
                            topLeft = Offset(
                                x = centerX - valueText.size.width / 2f,
                                y = barTop - valueText.size.height - 2f,
                            ),
                        )
                        val labelText = if (bucket.planTitle.length > 6) {
                            bucket.planTitle.take(5) + "…"
                        } else {
                            bucket.planTitle
                        }
                        val xLabel = textMeasurer.measure(labelText, style = labelStyle)
                        drawText(
                            textLayoutResult = xLabel,
                            topLeft = Offset(
                                x = centerX - xLabel.size.width / 2f,
                                y = plotTop + plotH + 4f,
                            ),
                        )
                    }
                }
            }
        }
    }
}
