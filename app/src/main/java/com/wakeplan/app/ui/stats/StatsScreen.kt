package com.wakeplan.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.domain.usecase.StatsUseCase
import com.wakeplan.app.ui.common.parseHexColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * 统计屏 — Phase 5.2.1
 *
 * 5.2.1: 数字面板 + range 切换 + per-plan 分布
 * 5.2.2: 加 Canvas 折线 + 柱状图（per-day / per-plan 视觉化）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("统计") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RangeToggle(
                current = state.range,
                onChange = { viewModel.selectRange(it) },
            )
            val result = state.result
            if (result == null) {
                // loading 或空 — 都给个空态
                EmptyStatsState()
            } else {
                NumberCards(result)
                PerDayLineChart(result)
                PerPlanList(result)
                PerPlanBarChart(result)
            }
        }
    }
}

@Composable
private fun RangeToggle(
    current: StatsUseCase.Range,
    onChange: (StatsUseCase.Range) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RangeButton(
            label = "近 7 天",
            selected = current == StatsUseCase.Range.LAST_7_DAYS,
            onClick = { onChange(StatsUseCase.Range.LAST_7_DAYS) },
            modifier = Modifier.weight(1f),
        )
        RangeButton(
            label = "近 30 天",
            selected = current == StatsUseCase.Range.LAST_30_DAYS,
            onClick = { onChange(StatsUseCase.Range.LAST_30_DAYS) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RangeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun NumberCards(result: StatsUseCase.StatsResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberCard(
                title = "已完成",
                value = "${result.totalCompleted}",
                modifier = Modifier.weight(1f),
            )
            NumberCard(
                title = "准时率",
                value = "${(result.onTimeRate * 100).toInt()}%",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberCard(
                title = "平均延期",
                value = if (result.avgOverrunMinutes == 0) "—" else "${result.avgOverrunMinutes} 分钟",
                modifier = Modifier.weight(1f),
            )
            NumberCard(
                title = "跳过/未完成",
                value = "${result.totalSkipped + result.totalPending}",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NumberCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PerPlanList(result: StatsUseCase.StatsResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "按计划完成数",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (result.perPlan.isEmpty()) {
                Text(
                    text = "这段时间还没完成任务",
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
                            style = MaterialTheme.typography.bodyMedium,
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "完成任务后，这里会显示统计概览",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ====================================================================
// Phase 5.2.2: 折线图（7/30 天完成趋势）
// ====================================================================

@Composable
private fun PerDayLineChart(result: StatsUseCase.StatsResult) {
    val perDay = result.perDay
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "每日完成数",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (perDay.all { it.completedCount == 0 }) {
                Text(
                    text = "这段时间还没完成过任务",
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
                    val padding = with(density) { 24.dp.toPx() }   // 给 Y 轴文字留位
                    val plotW = w - padding
                    val plotH = h - with(density) { 18.dp.toPx() } // 给 X 轴文字留位
                    val plotTop = with(density) { 4.dp.toPx() }
                    val plotLeft = padding

                    // 网格线（3 条：0, max/2, max；中间值跟 0/1 重合时跳过）
                    for (i in 0..2) {
                        val y = plotTop + plotH * (1f - i / 2f)
                        val label = ((maxY.toFloat() * i / 2f).toInt()).toString()
                        // 跳过重复 label（maxY=1, i=1 → "0" 跟 i=0 一样）
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

                    // 折线 + 点
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

                    // X 轴标签：第 1 个、中间、最后 1 个
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

// ====================================================================
// Phase 5.2.2: 柱状图（按计划完成数）
// ====================================================================

@Composable
private fun PerPlanBarChart(result: StatsUseCase.StatsResult) {
    val perPlan = result.perPlan
    val maxY = max(1, perPlan.maxOf { it.completedCount })
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
    val density = LocalDensity.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "按计划完成柱状",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (perPlan.isEmpty()) {
                Text(
                    text = "这段时间还没完成过任务",
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
                    val paddingBottom = with(density) { 28.dp.toPx() }   // X 轴标签
                    val paddingLeft = with(density) { 24.dp.toPx() }     // Y 轴标签
                    val plotW = w - paddingLeft
                    val plotH = h - paddingBottom
                    val plotTop = with(density) { 4.dp.toPx() }
                    val plotLeft = paddingLeft

                    // 0 基线
                    drawLine(
                        color = outlineColor,
                        start = Offset(plotLeft, plotTop + plotH),
                        end = Offset(plotLeft + plotW, plotTop + plotH),
                        strokeWidth = 1f,
                    )

                    // Y 轴标签：0, max/2, max；中间值跟 0/1 重合时跳过避免重复
                    for (i in 0..2) {
                        val yVal = (maxY.toFloat() * i / 2f).toInt()
                        val y = plotTop + plotH * (1f - i / 2f)
                        // 跳过中间值跟两端重复的（比如 maxY=1, i=1 → 0，跟 i=0 重复）
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

                    // 柱
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
                        // 柱顶数字
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
                        // X 轴标签：plan 标题（截断）
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
