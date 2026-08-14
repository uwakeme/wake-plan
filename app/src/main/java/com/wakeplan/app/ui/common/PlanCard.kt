package com.wakeplan.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.ui.plan.PlanCardData
import com.wakeplan.app.ui.plan.PlanIconKey
import com.wakeplan.app.ui.plan.PlanStatus
import com.wakeplan.app.ui.theme.WakePlanTheme
import java.time.LocalDate

/**
 * 计划列表卡片 — 视觉刷新 v0.4 的"标志性"组件
 *
 * 视觉规范（来源于 .superdesign/init/design-system.md §11 Plan Card），
 * 实际渲染参考 superdesign draft `2763b9c6-12ae-479e-8ef6-4795559c69a1`：
 *
 *   24dp 圆角（设计系统允许：large=16dp / xl=22dp，这里用 24dp 走"bold edition"）
 *   20dp padding、纯白 surface、1px subtle border、shadow-sm（多层）
 *   顶部行：40dp icon 方块（淡色 plan-color 底 + plan-color icon 20dp）
 *           | 标题 17sp semibold + 副标 "X 任务 · Y 分钟" 13sp text-tertiary
 *           | 状态徽章 11sp bold uppercase，8dp radius
 *   底部行：'Progress' 13sp semibold text-secondary + 'X%' 13sp semibold plan-color
 *           | 8dp 进度条（rounded-full，track=border-strong，fill=plan-color）
 *
 * 入场：stagger fade-in-up（80ms 错峰），用 index 计算 delay
 */
@Composable
fun PlanCard(
    data: PlanCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    val planColor = remember(data.plan.colorHex) {
        runCatching { parseHexColor(data.plan.colorHex) }.getOrElse { Color(0xFFC48520) }
    }
    val iconBg = planColor.copy(alpha = 0.10f)
    val statusLabel = when (data.status) {
        PlanStatus.ACTIVE -> stringResource(R.string.plan_card_status_active)
        PlanStatus.SCHEDULED -> stringResource(R.string.plan_card_status_scheduled)
        PlanStatus.IDLE -> stringResource(R.string.plan_card_status_idle)
    }

    // Stagger 入场 — 80ms 错峰
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 80L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 480)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 480),
                initialOffsetY = { fullHeight -> fullHeight / 6 },
            ),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---- 顶部行：icon + title/sub + status badge ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = data.iconKey.icon,
                            contentDescription = null,
                            tint = planColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = data.plan.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.plan_card_stats_tasks_minutes,
                                data.totalTaskCount,
                                data.todayDurationMinutes,
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    StatusBadge(label = statusLabel, accent = planColor)
                }

                // ---- 底部行：Progress 标签 + % + 进度条 ----
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.plan_card_progress_label),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.plan_card_progress_percent, data.progressPercent),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (data.progressPercent > 0) planColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProgressBar(
                        progress = data.progressPercent / 100f,
                        fillColor = planColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            ),
            color = accent,
        )
    }
}

@Composable
private fun ProgressBar(progress: Float, fillColor: Color) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(fillColor),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F0EC, widthDp = 480)
@Composable
private fun PlanCardPreviewActive() {
    WakePlanTheme {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(20.dp)) {
            PlanCard(
                data = sampleData(
                    title = "Fitness Routine",
                    colorHex = "#D87093",
                    iconKey = PlanIconKey.FITNESS,
                    status = PlanStatus.ACTIVE,
                    totalTaskCount = 12,
                    todayTaskCount = 4,
                    todayDurationMinutes = 90,
                    progressPercent = 50,
                ),
                onClick = {},
            )
            PlanCard(
                data = sampleData(
                    title = "Deep Study Session",
                    colorHex = "#3B6FB5",
                    iconKey = PlanIconKey.STUDY,
                    status = PlanStatus.SCHEDULED,
                    totalTaskCount = 18,
                    todayTaskCount = 6,
                    todayDurationMinutes = 180,
                    progressPercent = 17,
                ),
                onClick = {},
            )
            PlanCard(
                data = sampleData(
                    title = "Novel Writing",
                    colorHex = "#2E9B5E",
                    iconKey = PlanIconKey.WRITING,
                    status = PlanStatus.IDLE,
                    totalTaskCount = 8,
                    todayTaskCount = 0,
                    todayDurationMinutes = 0,
                    progressPercent = 0,
                ),
                onClick = {},
            )
        }
    }
}

private fun sampleData(
    title: String,
    colorHex: String,
    iconKey: PlanIconKey,
    status: PlanStatus,
    totalTaskCount: Int,
    todayTaskCount: Int,
    todayDurationMinutes: Int,
    progressPercent: Int,
): PlanCardData = PlanCardData(
    plan = Plan(
        id = "p1",
        title = title,
        colorHex = colorHex,
        startDate = LocalDate.of(2026, 6, 15),
        isActive = status != PlanStatus.IDLE,
        createdAt = java.time.Instant.now(),
        updatedAt = java.time.Instant.now(),
    ),
    iconKey = iconKey,
    status = status,
    totalTaskCount = totalTaskCount,
    todayTaskCount = todayTaskCount,
    todayDurationMinutes = todayDurationMinutes,
    progressPercent = progressPercent,
)
