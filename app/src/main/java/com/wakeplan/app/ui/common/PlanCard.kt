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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
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
import com.wakeplan.app.ui.theme.WpSuccessDark
import java.time.LocalDate

@Composable
fun PlanCard(
    data: PlanCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    val defaultColor = MaterialTheme.colorScheme.primary
    val planColor = remember(data.plan.colorHex) {
        runCatching { parseHexColor(data.plan.colorHex) }.getOrElse { defaultColor }
    }
    val iconBg = planColor.copy(alpha = 0.12f)
    val statusLabel = when (data.status) {
        PlanStatus.ACTIVE -> stringResource(R.string.plan_card_status_active)
        PlanStatus.SCHEDULED -> stringResource(R.string.plan_card_status_scheduled)
        PlanStatus.IDLE -> stringResource(R.string.plan_card_status_idle)
    }
    val statusColor = when (data.status) {
        PlanStatus.ACTIVE -> WpSuccessDark
        PlanStatus.SCHEDULED -> WpSuccessDark
        PlanStatus.IDLE -> MaterialTheme.colorScheme.tertiary
    }

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
            shape = RoundedCornerShape(WpRadius.xxl),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(planColor)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(WpRadius.md))
                            .background(iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = data.iconKey.icon,
                            contentDescription = null,
                            tint = planColor,
                            modifier = Modifier.size(22.dp),
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
                                fontSize = 17.sp,
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
                                fontSize = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusBadge(label = statusLabel, accent = statusColor)
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(WpRadius.full))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
            color = accent,
        )
    }
}

@Preview(showBackground = true, widthDp = 480)
@Composable
private fun PlanCardPreviewActive() {
    WakePlanTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
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
