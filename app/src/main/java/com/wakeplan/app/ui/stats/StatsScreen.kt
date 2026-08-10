package com.wakeplan.app.ui.stats

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.domain.usecase.StatsUseCase
import com.wakeplan.app.ui.common.parseHexColor

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
                PerPlanList(result)
                // 5.2.2 在这里加 PerDayChart + 其它
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
