package com.wakeplan.app.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TimeSegment
import java.time.DayOfWeek
import androidx.compose.ui.res.stringResource
import com.wakeplan.app.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDetailScreen(
    onBack: () -> Unit,
    onApply: (newPlanId: String) -> Unit = {},
    viewModel: TemplateDetailViewModel = hiltViewModel(),
) {
    val template by viewModel.template.collectAsStateWithLifecycle()
    val applyState by viewModel.applyState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 监听 applyState 变化：Success → onApply(newPlanId) + 重置；Error → Snackbar
    LaunchedEffect(applyState) {
        when (val s = applyState) {
            is TemplateDetailViewModel.ApplyState.Success -> {
                onApply(s.newPlanId)
                viewModel.consumeApplyResult()
            }
            is TemplateDetailViewModel.ApplyState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.consumeApplyResult()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template?.title ?: stringResource(R.string.tpl_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val current = template
        if (current == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.tpl_detail_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        TemplateDetailBody(
            template = current,
            applyState = applyState,
            contentPadding = padding,
            onApplyClick = { viewModel.applyTemplate() },
        )
    }
}

@Composable
private fun TemplateDetailBody(
    template: PlanTemplate,
    applyState: TemplateDetailViewModel.ApplyState,
    contentPadding: PaddingValues,
    onApplyClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TemplateHeader(template) }
        item { ApplyButtonRow(applyState = applyState, onClick = onApplyClick) }

        if (template.segments.isEmpty()) {
            item {
                EmptySegmentsHint(
                    summary = if (template.isBuiltIn) stringResource(R.string.tpl_detail_empty_built_in) else stringResource(R.string.tpl_detail_empty_user),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            val tasksBySegment = template.tasks.groupBy { it.segmentId }
            items(template.segments, key = { it.id }) { seg ->
                TemplateSegmentCard(
                    segment = seg,
                    tasks = tasksBySegment[seg.id].orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun TemplateHeader(template: PlanTemplate) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(template.colorHex))),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = if (template.isBuiltIn) stringResource(R.string.tpl_list_label_built_in) else stringResource(R.string.tpl_list_label_user),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.tpl_detail_segments_tasks_count_runtime, template.segments.size, template.tasks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplyButtonRow(
    applyState: TemplateDetailViewModel.ApplyState,
    onClick: () -> Unit,
) {
    val running = applyState is TemplateDetailViewModel.ApplyState.Running
    Button(
        onClick = onClick,
        enabled = !running,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (running) stringResource(R.string.tpl_detail_creating_runtime) else stringResource(R.string.tpl_detail_apply))
    }
}

@Composable
private fun EmptySegmentsHint(summary: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun TemplateSegmentCard(
    segment: TimeSegment,
    tasks: List<Task>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 段头
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${formatTime(segment.startMinutes)} – ${formatTime(segment.endMinutes)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = repeatLabel(context, segment),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (segment.title != null) {
                    Text(
                        text = segment.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 任务列表
            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.tpl_detail_no_segment_runtime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tasks.forEach { task ->
                    TemplateTaskRow(task)
                }
            }
        }
    }
}

@Composable
private fun TemplateTaskRow(task: Task) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (task.note != null) {
                Text(
                    text = task.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.tpl_detail_duration_min_runtime, task.plannedDurationMinutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (task.isLocked) {
            Spacer(Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.today_pinned),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

// ============================================================
// helpers（跟 PlanDetailScreen 复用同一份 formatTime / repeatLabel）
// ============================================================

private fun formatTime(minutes: Int): String {
    val total = ((minutes % 1440) + 1440) % 1440
    val h = total / 60
    val m = total % 60
    val dayShift = if (minutes >= 1440) " (+1)" else ""
    return "%02d:%02d%s".format(h, m, dayShift)
}

private fun repeatLabel(context: android.content.Context, seg: TimeSegment): String = when (seg.repeatRule) {
    RepeatRule.ONCE -> context.getString(R.string.plan_seg_repeat_once)
    RepeatRule.WEEKDAYS -> context.getString(R.string.plan_seg_repeat_weekdays)
    RepeatRule.DAILY -> context.getString(R.string.plan_seg_repeat_daily)
    RepeatRule.CUSTOM -> {
        if (seg.customDays.isEmpty()) context.getString(R.string.plan_seg_repeat_custom)
        else {
            val order = listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            )
            order.filter { it in seg.customDays }
                .joinToString("") {
                    when (it) {
                        DayOfWeek.MONDAY -> context.getString(R.string.tpl_detail_dow_mon)
                        DayOfWeek.TUESDAY -> context.getString(R.string.tpl_detail_dow_tue)
                        DayOfWeek.WEDNESDAY -> context.getString(R.string.tpl_detail_dow_wed)
                        DayOfWeek.THURSDAY -> context.getString(R.string.tpl_detail_dow_thu)
                        DayOfWeek.FRIDAY -> context.getString(R.string.tpl_detail_dow_fri)
                        DayOfWeek.SATURDAY -> context.getString(R.string.tpl_detail_dow_sat)
                        DayOfWeek.SUNDAY -> context.getString(R.string.tpl_detail_dow_sun)
                        else -> "?"
                    }
                }
        }
    }
}
