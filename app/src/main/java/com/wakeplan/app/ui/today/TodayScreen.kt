package com.wakeplan.app.ui.today

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.model.TimeSegment
import com.wakeplan.app.domain.usecase.TaskConflict
import com.wakeplan.app.ui.common.HighlightBus
import com.wakeplan.app.ui.common.parseHexColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 当前时刻屏 — Phase 2.4b
 *
 * 布局：
 * 1. TopAppBar (今日日期)
 * 2. 24h 水平时间线（段背景 + 任务色块 + 当前时刻红线 + 时间刻度）
 * 3. 跨计划卡片列表（每 plan 一卡 + 段内任务 + 开始/完成/跳过按钮 + 内联冲突警告）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen() {
    val viewModel: TodayViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highlightedTaskId by HighlightBus.highlightedTaskId.collectAsState()
    val undoableAction by viewModel.undoableAction.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.ensureTodayCompletions()
    }

    // Deep link 触发的高亮：滚动到对应 plan item + 5s 后清掉
    LaunchedEffect(highlightedTaskId, state.planItems) {
        val targetId = highlightedTaskId ?: return@LaunchedEffect
        val targetIndex = state.planItems.indexOfFirst { planItem ->
            planItem.tasks.any { it.id == targetId }
        }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex + 1)  // +1 是 timeline item
            delay(5_000L)
            HighlightBus.clear()
        }
    }

    // 撤销 Snackbar：5s 窗口，点了撤销调 viewModel.undo()
    LaunchedEffect(undoableAction) {
        val action = undoableAction ?: return@LaunchedEffect
        val message = when (action) {
            is TodayViewModel.UndoableAction.Start -> "已开始"
            is TodayViewModel.UndoableAction.Complete -> "已完成"
            is TodayViewModel.UndoableAction.Skip -> "已跳过"
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "撤销",
            withDismissAction = true,
            duration = SnackbarDuration.Short,  // ~5s
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undo()
            SnackbarResult.Dismissed -> viewModel.consumeUndo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日 ${state.date.format(DATE_FMT)}") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.planItems.isEmpty()) {
            EmptyTodayState(modifier = Modifier.padding(padding))
        } else {
            TodayContent(
                state = state,
                contentPadding = padding,
                listState = listState,
                highlightedTaskId = highlightedTaskId,
                onStartTask = viewModel::onStartTask,
                onCompleteTask = viewModel::onCompleteTask,
                onSkipTask = viewModel::onSkipTask,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayContent(
    state: TodayUiState,
    contentPadding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    highlightedTaskId: String?,
    onStartTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onSkipTask: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 24h 时间线
        item(key = "timeline") {
            TodayTimeLine(
                date = state.date,
                now = state.now,
                planItems = state.planItems,
                conflicts = state.conflicts,
            )
        }
        // 跨计划卡片
        items(state.planItems, key = { "plan-${it.plan.id}" }) { item ->
            val planConflicts = state.conflicts.filter { c ->
                c.taskA.planId == item.plan.id || c.taskB.planId == item.plan.id
            }
            TodayPlanCard(
                item = item,
                adjustedCompletions = state.adjustedCompletions.filter { it.planId == item.plan.id },
                conflicts = planConflicts,
                plansById = state.planItems.associate { it.plan.id to it.plan },
                highlightedTaskId = highlightedTaskId,
                onStartTask = onStartTask,
                onCompleteTask = onCompleteTask,
                onSkipTask = onSkipTask,
            )
        }
    }
}

// ====================================================================
// 24h 水平时间线
// ====================================================================

@Composable
private fun TodayTimeLine(
    date: LocalDate,
    now: LocalDateTime,
    planItems: List<TodayPlanItem>,
    conflicts: List<TaskConflict>,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tickLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    val timelineHeight = 80.dp
    val rowHeight = 14.dp  // 每条 plan 行高
    val rowGap = 2.dp
    val nowLineColor = MaterialTheme.colorScheme.error
    val conflictGlow = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)

    // 跨午夜段 / 任务 截断到 24:00 (简化：MVP 不画 0-24h 之外的部分)
    val maxMinutes = 24 * 60

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "今日时间线",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timelineHeight + 18.dp)  // 留出时间刻度文字高度
            ) {
                val w = size.width
                val tlHeight = with(density) { timelineHeight.toPx() }
                val minuteWidth = w / maxMinutes.toFloat()
                val rowPx = with(density) { rowHeight.toPx() }
                val rowGapPx = with(density) { rowGap.toPx() }

                // 底色
                drawRect(
                    color = outlineColor.copy(alpha = 0.3f),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, tlHeight),
                )

                // 每行画一个 plan
                planItems.forEachIndexed { idx, item ->
                    val planColor = parseHexColor(item.plan.colorHex)
                    val rowTop = idx * (rowPx + rowGapPx)

                    // 段背景（半透明）
                    item.segments.forEach { seg ->
                        val startMin = seg.startMinutes.coerceIn(0, maxMinutes)
                        val endMinRaw = if (seg.endMinutes > maxMinutes) maxMinutes else seg.endMinutes
                        val endMin = endMinRaw.coerceAtLeast(startMin + 1)
                        drawRect(
                            color = planColor.copy(alpha = 0.2f),
                            topLeft = Offset(startMin * minuteWidth, rowTop),
                            size = Size((endMin - startMin) * minuteWidth, rowPx),
                        )
                    }

                    // 任务色块（不透明）
                    item.completions.forEach { c ->
                        val startMin = minuteOfDay(c.plannedStart, date).coerceIn(0, maxMinutes)
                        val endMin = minuteOfDay(c.plannedEnd, date).coerceIn(startMin + 1, maxMinutes)
                        drawRect(
                            color = planColor,
                            topLeft = Offset(startMin * minuteWidth, rowTop + 1),
                            size = Size((endMin - startMin) * minuteWidth, rowPx - 2),
                        )
                    }
                }

                // 当前时刻红线
                val nowMin = minuteOfDay(now, date).coerceIn(0, maxMinutes)
                drawLine(
                    color = nowLineColor,
                    start = Offset(nowMin * minuteWidth, 0f),
                    end = Offset(nowMin * minuteWidth, tlHeight),
                    strokeWidth = 2f,
                )

                // 时间刻度文字（条下方）
                val ticks = listOf(0, 6, 12, 18, 24)
                ticks.forEach { h ->
                    val x = h * 60 * minuteWidth
                    val text = if (h == 24) "24:00" else String.format("%02d:00", h)
                    val measured = textMeasurer.measure(
                        text = text,
                        style = tickLabelStyle,
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = text,
                        topLeft = Offset(
                            x - measured.size.width / 2f,
                            tlHeight + 2f,
                        ),
                        style = tickLabelStyle,
                    )
                }
            }
        }
    }
}

private fun minuteOfDay(dt: LocalDateTime, date: LocalDate): Int {
    // 不区分日期，只看时分（跨午夜段如果 dt 是次日，minuteOfDay > 24*60）
    val dayOffset = (dt.toLocalDate().toEpochDay() - date.toEpochDay()).toInt()
    return dt.hour * 60 + dt.minute + dayOffset * 24 * 60
}

// ====================================================================
// 跨计划卡片
// ====================================================================

@Composable
private fun TodayPlanCard(
    item: TodayPlanItem,
    adjustedCompletions: List<TaskCompletion>,
    conflicts: List<TaskConflict>,
    plansById: Map<String, Plan>,
    highlightedTaskId: String?,
    onStartTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onSkipTask: (String) -> Unit,
) {
    val planColor = parseHexColor(item.plan.colorHex)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 计划标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(planColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // 内联冲突警告
            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ConflictBanner(conflicts = conflicts, plansById = plansById)
            }

            Spacer(Modifier.height(12.dp))

            // 段内任务列表
            val bySegment = item.segments
                .sortedBy { it.startMinutes }
                .map { seg ->
                    seg to adjustedCompletions
                        .filter { it.taskId in item.tasks.filter { t -> t.segmentId == seg.id }.map { it.id } }
                        .sortedBy { it.plannedStart }
                }
                .filter { (_, completions) -> completions.isNotEmpty() }

            if (bySegment.isEmpty()) {
                Text(
                    text = "今日无任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val tasksById = item.tasks.associateBy { it.id }
                bySegment.forEachIndexed { i, (seg, completions) ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    SegmentSection(
                        segment = seg,
                        completions = completions,
                        tasksById = tasksById,
                        highlightedTaskId = highlightedTaskId,
                        onStartTask = onStartTask,
                        onCompleteTask = onCompleteTask,
                        onSkipTask = onSkipTask,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentSection(
    segment: TimeSegment,
    completions: List<TaskCompletion>,
    tasksById: Map<String, com.wakeplan.app.domain.model.Task>,
    highlightedTaskId: String?,
    onStartTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onSkipTask: (String) -> Unit,
) {
    Column {
        Text(
            text = buildString {
                append(segment.title ?: "段")
                append(" · ")
                append(formatMinutes(segment.startMinutes))
                if (segment.endMinutes > 24 * 60) {
                    append(" — 次日 ")
                    append(formatMinutes(segment.endMinutes - 24 * 60))
                } else {
                    append(" — ")
                    append(formatMinutes(segment.endMinutes))
                }
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        completions.forEach { c ->
            val title = tasksById[c.taskId]?.title ?: c.taskId.take(8) + "…"
            TaskRow(
                completion = c,
                taskTitle = title,
                highlighted = c.taskId == highlightedTaskId,
                onStart = { onStartTask(c.id) },
                onComplete = { onCompleteTask(c.id) },
                onSkip = { onSkipTask(c.id) },
            )
        }
    }
}

@Composable
private fun TaskRow(
    completion: TaskCompletion,
    taskTitle: String,
    highlighted: Boolean = false,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer
             else androidx.compose.ui.graphics.Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态图标
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (completion.status) {
                TaskStatus.PENDING -> Text(
                    "○",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                TaskStatus.IN_PROGRESS -> Text(
                    "▶",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TaskStatus.COMPLETED -> Text(
                    "✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TaskStatus.SKIPPED -> Text(
                    "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = taskTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (completion.status == TaskStatus.COMPLETED)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildString {
                    append(formatTime(completion.plannedStart))
                    append(" — ")
                    append(formatTime(completion.plannedEnd))
                    if (completion.status == TaskStatus.IN_PROGRESS && completion.overrunMinutes > 0) {
                        append("  +${completion.overrunMinutes}min")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        when (completion.status) {
            TaskStatus.PENDING -> {
                Button(onClick = onStart) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "开始")
                    Spacer(Modifier.width(4.dp))
                    Text("开始")
                }
            }
            TaskStatus.IN_PROGRESS -> {
                Button(onClick = onComplete) {
                    Icon(Icons.Outlined.Check, contentDescription = "完成")
                    Spacer(Modifier.width(4.dp))
                    Text("完成")
                }
            }
            TaskStatus.COMPLETED, TaskStatus.SKIPPED -> {
                // 终态 — 显示 icon only
                Icon(
                    imageVector = if (completion.status == TaskStatus.COMPLETED) Icons.Outlined.Check else Icons.Outlined.Close,
                    contentDescription = if (completion.status == TaskStatus.COMPLETED) "已完成" else "已跳过",
                    tint = if (completion.status == TaskStatus.COMPLETED)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (completion.status == TaskStatus.PENDING || completion.status == TaskStatus.IN_PROGRESS) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onSkip) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "跳过",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConflictBanner(
    conflicts: List<TaskConflict>,
    plansById: Map<String, Plan>,
) {
    val firstConflict = conflicts.first()
    val otherPlan = plansById[
        if (firstConflict.taskA.planId == plansById.values.firstOrNull { it.id == firstConflict.taskA.planId }?.id)
            firstConflict.taskB.planId
        else firstConflict.taskA.planId
    ]
    val maxOverlap = conflicts.maxOf { it.overlapMinutes }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "与 ${otherPlan?.title ?: "其他计划"} 冲突 $maxOverlap 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun EmptyTodayState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "今天还没有计划",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "去「计划」tab 创建一个",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ====================================================================
// helpers
// ====================================================================

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

private fun formatTime(dt: LocalDateTime): String = dt.format(TIME_FMT)
private fun formatMinutes(min: Int): String = String.format("%02d:%02d", min / 60, min % 60)

@Suppress("unused")
private val Unused: Color = Color.Unspecified
