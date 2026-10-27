package com.wakeplan.app.ui.focus

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.R
import com.wakeplan.app.focus.FocusPhase
import com.wakeplan.app.focus.FocusState
import com.wakeplan.app.notification.NotificationBuilder
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * 专注页 — v0.6 绑任务的番茄钟
 *
 * 两种形态：
 * - 空闲：目标任务信息 + 配置摘要 + "开始专注"（controller 忙别的任务时提示）
 * - 计时中：大倒计时环 + 阶段/轮次 + 完成打卡 / 跳过 / 放弃
 */
@Composable
fun FocusScreen(
    onExit: () -> Unit,
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val target by viewModel.target.collectAsStateWithLifecycle()
    val focusState by viewModel.focusState.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val startConflict by viewModel.startConflict.collectAsStateWithLifecycle()

    val state = focusState
    val active = state != null && state.completionId == viewModel.completionId

    // 本屏曾处于计时中：整场结束（完成/放弃）后自动退出回 Today
    var wasActive by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (active) wasActive = true
    LaunchedEffect(focusState) {
        if (focusState == null && wasActive) {
            onExit()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp)
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.focus_screen_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        val t = target
        if (t == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.common_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        if (active && state != null) {
            ActiveFocusContent(
                state = state,
                onComplete = viewModel::completeTask,
                onSkip = viewModel::skipPhase,
                onAbandon = viewModel::abandon,
            )
        } else {
            IdleFocusContent(
                taskTitle = t.task.title,
                planDurationHint = stringResource(
                    R.string.focus_idle_config_summary,
                    config.focusMinutes, config.shortBreakMinutes, config.roundsBeforeLongBreak,
                ),
                busyWithOther = focusState != null,
                conflictTitle = startConflict,
                onStart = viewModel::start,
            )
        }
    }
}

@Composable
private fun ActiveFocusContent(
    state: FocusState,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onAbandon: () -> Unit,
) {
    // 每秒刷新的"现在"（倒计时显示用；状态真源 phaseEndsAt 是墙钟）
    val now by produceState(Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(1_000L)
        }
    }

    val remaining = Duration.between(now, state.phaseEndsAt).let {
        if (it.isNegative) Duration.ZERO else it
    }
    val phaseTotalSeconds = state.phaseMinutes * 60L
    val elapsedSeconds = (phaseTotalSeconds - remaining.seconds).coerceIn(0, phaseTotalSeconds)
    val progress = if (phaseTotalSeconds > 0) elapsedSeconds.toFloat() / phaseTotalSeconds else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = when (state.phase) {
                FocusPhase.FOCUS -> stringResource(R.string.focus_phase_focus)
                FocusPhase.SHORT_BREAK -> stringResource(R.string.focus_phase_short_break)
                FocusPhase.LONG_BREAK -> stringResource(R.string.focus_phase_long_break)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.tertiary,
        )

        Spacer(Modifier.height(20.dp))

        TimerRing(
            progress = progress,
            remainingText = NotificationBuilder.formatRemaining(remaining),
            focusPhase = state.phase == FocusPhase.FOCUS,
        )

        Spacer(Modifier.height(20.dp))

        // 轮次点：本场已完成的专注轮（对 roundsBeforeLongBreak 取模显示当前周期）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cycle = state.completedFocusRounds % state.config.roundsBeforeLongBreak
            repeat(state.config.roundsBeforeLongBreak) { i ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (i < cycle) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
        Text(
            text = stringResource(R.string.focus_round_count, state.completedFocusRounds),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = state.taskTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(12.dp))
        if (!state.running) {
            Text(
                text = stringResource(R.string.focus_waiting_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.phase == FocusPhase.FOCUS) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.focus_action_complete), fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Text(
                    text = if (state.running) {
                        stringResource(R.string.focus_action_skip)
                    } else {
                        stringResource(R.string.focus_action_next)
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onAbandon) {
                Text(
                    stringResource(R.string.focus_action_abandon),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TimerRing(
    progress: Float,
    remainingText: String,
    focusPhase: Boolean,
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = if (focusPhase) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(248.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = size.width - inset * 2
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(ringColor, ringColor.copy(alpha = 0.75f), ringColor)),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = remainingText,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun IdleFocusContent(
    taskTitle: String,
    planDurationHint: String,
    busyWithOther: Boolean,
    conflictTitle: String?,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = taskTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = planDurationHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        if (conflictTitle != null) {
            Text(
                text = if (conflictTitle.isBlank()) {
                    stringResource(R.string.focus_conflict_in_progress_noname)
                } else {
                    stringResource(R.string.focus_conflict_in_progress, conflictTitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (busyWithOther) {
            Text(
                text = stringResource(R.string.focus_busy_other),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Button(
            onClick = onStart,
            enabled = !busyWithOther,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = stringResource(R.string.focus_start),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
