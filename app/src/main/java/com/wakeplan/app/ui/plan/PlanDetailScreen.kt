package com.wakeplan.app.ui.plan

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TimeSegment
import java.time.DayOfWeek

/**
 * 计划详情屏
 * 顶部计划信息 + 段列表（每段内嵌任务列表）+ FAB 加段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onAddSegment: () -> Unit,
    onSegmentClick: (segmentId: String) -> Unit,
    onAddTask: (segmentId: String) -> Unit,
    onTaskClick: (segmentId: String, taskId: String) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val tasksBySegment by viewModel.tasksBySegment.collectAsStateWithLifecycle()
    var showDeletePlanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan?.title ?: stringResource(R.string.plan_detail_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (plan != null) {
                        IconButton(onClick = { showDeletePlanDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.plan_detail_cd_delete))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSegment) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.plan_detail_cd_add_segment))
            }
        },
    ) { padding ->
        val currentPlan = plan
        if (currentPlan == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.common_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        PlanDetailBody(
            plan = currentPlan,
            segments = segments,
            tasksBySegment = tasksBySegment,
            contentPadding = padding,
            onSegmentClick = onSegmentClick,
            onAddTask = onAddTask,
            onTaskClick = onTaskClick,
            onDeleteSegment = viewModel::deleteSegment,
            onDeleteTask = viewModel::deleteTask,
        )
    }

    if (showDeletePlanDialog && plan != null) {
        AlertDialog(
            onDismissRequest = { showDeletePlanDialog = false },
            title = { Text(stringResource(R.string.plan_detail_delete_dialog_title)) },
            text = {
                Text(stringResource(R.string.plan_detail_delete_dialog_text, plan!!.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePlanDialog = false
                        viewModel.deletePlan(onDone = onBack)
                    }
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlanDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun PlanDetailBody(
    plan: Plan,
    segments: List<TimeSegment>,
    tasksBySegment: Map<String, List<Task>>,
    contentPadding: PaddingValues,
    onSegmentClick: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onTaskClick: (segmentId: String, taskId: String) -> Unit,
    onDeleteSegment: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PlanHeader(plan) }

        if (segments.isEmpty()) {
            item {
                EmptySegmentsHint(modifier = Modifier.fillMaxWidth())
            }
        } else {
            items(segments, key = { it.id }) { seg ->
                SegmentCard(
                    segment = seg,
                    tasks = tasksBySegment[seg.id].orEmpty(),
                    onSegmentClick = { onSegmentClick(seg.id) },
                    onAddTask = { onAddTask(seg.id) },
                    onTaskClick = onTaskClick,
                    onDeleteSegment = { onDeleteSegment(seg.id) },
                    onDeleteTask = onDeleteTask,
                )
            }
        }
    }
}

@Composable
private fun PlanHeader(plan: Plan) {
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
                    .background(Color(android.graphics.Color.parseColor(plan.colorHex))),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.plan_detail_header_subtitle,
                        plan.startDate.toString(),
                        stringResource(if (plan.isActive) R.string.plan_detail_status_active else R.string.plan_detail_status_archived),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptySegmentsHint(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.plan_detail_no_segments),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun SegmentCard(
    segment: TimeSegment,
    tasks: List<Task>,
    onSegmentClick: () -> Unit,
    onAddTask: () -> Unit,
    onTaskClick: (segmentId: String, taskId: String) -> Unit,
    onDeleteSegment: () -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 段头：时间段 + repeat 标识 + 编辑/删除
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
                        text = rememberRepeatLabel(segment),
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
                IconButton(onClick = onSegmentClick) {
                    Icon(Icons.Outlined.Schedule, contentDescription = stringResource(R.string.plan_detail_segment_cd_edit))
                }
                IconButton(onClick = onDeleteSegment) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.plan_detail_segment_cd_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 任务列表
            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.plan_detail_no_tasks_in_segment),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tasks.forEach { task ->
                    TaskRow(
                        task = task,
                        onClick = { onTaskClick(segment.id, task.id) },
                        onDelete = { onDeleteTask(task.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAddTask) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.plan_detail_add_task))
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
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
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.common_minutes, task.plannedDurationMinutes),
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
                    text = stringResource(R.string.common_pinned),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Schedule, contentDescription = stringResource(R.string.plan_detail_task_cd_edit))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.plan_detail_task_cd_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// 顶层函数：根据 task 在 segment 中查 segmentId（用于 nav 路由）
@Suppress("unused")
private fun Task.segmentIdOrEmpty(): String = segmentId

// 辅助：把分钟数（0..1440，跨日可 >1440）格式化为 HH:mm
private fun formatTime(minutes: Int): String {
    val total = ((minutes % 1440) + 1440) % 1440  // 折叠到 0..1439
    val h = total / 60
    val m = total % 60
    val dayShift = if (minutes >= 1440) " (+1)" else ""
    return "%02d:%02d%s".format(h, m, dayShift)
}

@Composable
private fun rememberRepeatLabel(seg: TimeSegment): String {
    val once = stringResource(R.string.repeat_once)
    val weekdays = stringResource(R.string.repeat_weekdays)
    val daily = stringResource(R.string.repeat_daily)
    val custom = stringResource(R.string.repeat_custom)
    val mon = stringResource(R.string.weekday_mon)
    val tue = stringResource(R.string.weekday_tue)
    val wed = stringResource(R.string.weekday_wed)
    val thu = stringResource(R.string.weekday_thu)
    val fri = stringResource(R.string.weekday_fri)
    val sat = stringResource(R.string.weekday_sat)
    val sun = stringResource(R.string.weekday_sun)
    return when (seg.repeatRule) {
        com.wakeplan.app.domain.model.RepeatRule.ONCE -> once
        com.wakeplan.app.domain.model.RepeatRule.WEEKDAYS -> weekdays
        com.wakeplan.app.domain.model.RepeatRule.DAILY -> daily
        com.wakeplan.app.domain.model.RepeatRule.CUSTOM -> {
            if (seg.customDays.isEmpty()) custom
            else {
                val order = listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
                )
                val labelByDay = mapOf(
                    DayOfWeek.MONDAY to mon,
                    DayOfWeek.TUESDAY to tue,
                    DayOfWeek.WEDNESDAY to wed,
                    DayOfWeek.THURSDAY to thu,
                    DayOfWeek.FRIDAY to fri,
                    DayOfWeek.SATURDAY to sat,
                    DayOfWeek.SUNDAY to sun,
                )
                order.filter { it in seg.customDays }.joinToString("") { labelByDay[it] ?: "?" }
            }
        }
    }
}
