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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.usecase.TaskConflict
import com.wakeplan.app.ui.common.HighlightBus
import com.wakeplan.app.ui.common.WpAnimateInContainer
import com.wakeplan.app.ui.common.WpRadius
import com.wakeplan.app.ui.common.parseHexColor
import com.wakeplan.app.ui.plan.PlanIconKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val startedMsg = stringResource(R.string.today_status_started)
    val completedMsg = stringResource(R.string.stats_metric_completed)
    val skippedMsg = stringResource(R.string.today_status_skipped)
    val undoLabel = stringResource(R.string.today_undo)
    LaunchedEffect(undoableAction) {
        val action = undoableAction ?: return@LaunchedEffect
        val message = when (action) {
            is TodayViewModel.UndoableAction.Start -> startedMsg
            is TodayViewModel.UndoableAction.Complete -> completedMsg
            is TodayViewModel.UndoableAction.Skip -> skippedMsg
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Short,  // ~5s
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undo()
            SnackbarResult.Dismissed -> viewModel.consumeUndo()
        }
    }

    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val isViewingToday by viewModel.isViewingToday.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isViewingToday) {
                            stringResource(R.string.today_title_today, state.date.format(dateFmt(context)))
                        } else {
                            stringResource(R.string.today_title_other, state.date.format(dateFmt(context)))
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.switchDate(state.date.minusDays(1)) },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.today_cd_prev_day),
                        )
                    }
                },
                actions = {
                    if (!isViewingToday) {
                        TextButton(onClick = { viewModel.goToToday() }) {
                            Text(stringResource(R.string.today_btn_today))
                        }
                    }
                    IconButton(
                        onClick = { viewModel.switchDate(state.date.plusDays(1)) },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.today_cd_next_day),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.planItems.isEmpty()) {
            EmptyTodayState(
                isViewingToday = isViewingToday,
                date = state.date,
                modifier = Modifier.padding(padding),
            )
        } else {
            TodayContent(
                state = state,
                isViewingToday = isViewingToday,
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
    isViewingToday: Boolean,
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 24h 时间线
        item(key = "timeline") {
            TodayTimeLine(
                date = state.date,
                isViewingToday = isViewingToday,
                now = state.now,
                planItems = state.planItems,
                conflicts = state.conflicts,
            )
        }

        // v0.4：扁平任务列表（按时间排，跨 plan 混合）
        // 每行带 plan 颜色 + 名称标签，让用户知道是哪个计划的任务
        itemsIndexed(state.todayTaskItems, key = { _, item -> "task-${item.completion.id}" }) { index, item ->
            val planConflicts = state.conflicts.filter { c ->
                c.taskA.planId == item.plan.id || c.taskB.planId == item.plan.id
            }
            WpAnimateInContainer(index = index + 1) {
                TodayTaskCard(
                    item = item,
                    conflicts = planConflicts,
                    highlighted = item.completion.taskId == highlightedTaskId,
                    onStart = { onStartTask(item.completion.id) },
                    onComplete = { onCompleteTask(item.completion.id) },
                    onSkip = { onSkipTask(item.completion.id) },
                )
            }
        }
    }
}

// ====================================================================
// 24h 水平时间线
// ====================================================================

@Composable
private fun TodayTimeLine(
    date: LocalDate,
    isViewingToday: Boolean,
    now: LocalDateTime,
    planItems: List<TodayPlanItem>,
    conflicts: List<TaskConflict>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tickLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val nowLineColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outlineVariant  // 提到 Composable 作用域，Canvas 里复用

    // ---- 布局尺寸 ----
    val labelColumnWidth = 64.dp       // 左侧 plan 名列宽度
    val rowGap = 6.dp                 // plan lane 之间的间距
    val laneMinHeight = 22.dp          // 每条 plan lane 最小高度（task 块可能更矮）
    val blockHeight = 20.dp            // task 色块高度
    val blockCornerRadius = 5.dp
    val hourAxisHeight = 18.dp         // 底部整点刻度行
    val maxMinutes = 24 * 60
    val nowTimeText = String.format("%02d:%02d", now.hour, now.minute)
    // 没有 plan 时给个最小高度，避免空白
    val lanesTotal = planItems.size.coerceAtLeast(1)
    val trackHeight = (laneMinHeight * lanesTotal) + (rowGap * (lanesTotal - 1).coerceAtLeast(0))

    val titleText = if (isViewingToday) {
        stringResource(R.string.today_timeline_title_today)
    } else {
        stringResource(R.string.today_timeline_title_other, date.format(dateFmt(context)))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WpRadius.xxl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ---- 头部行：标题 + 当前时间 pill ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isViewingToday) {
                    NowTimePill(time = nowTimeText, color = nowLineColor)
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- 主体：左 plan 名 + 右时间轨 ----
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左侧 plan 名称列
                PlanLabelColumn(
                    planItems = planItems,
                    rowGap = rowGap,
                    labelColumnWidth = labelColumnWidth,
                    laneMinHeight = laneMinHeight,
                    rowHeight = blockHeight,
                )
                Spacer(Modifier.width(8.dp))
                // 右侧时间轨（Canvas）
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(trackHeight + hourAxisHeight),
                ) {
                    val w = size.width
                    val tlHeight = trackHeight.toPx()
                    val minuteWidth = w / maxMinutes.toFloat()
                    val rowPx = blockHeight.toPx()
                    val rowGapPx = rowGap.toPx()
                    val cornerPx = with(density) { blockCornerRadius.toPx() }

                    // 每条 plan lane：先画淡底，再画 task 块
                    planItems.forEachIndexed { idx, item ->
                        val planColor = parseHexColor(item.plan.colorHex)
                        val rowCenter = idx * (rowPx + rowGapPx) + rowPx / 2f

                        // lane 中心水平细线（plan color 30% 透明）— 当作"基线"
                        drawLine(
                            color = planColor.copy(alpha = 0.25f),
                            start = Offset(0f, rowCenter),
                            end = Offset(w, rowCenter),
                            strokeWidth = 1f,
                        )

                        // task 色块（带 plan color 淡边 + 稍深的填充）
                        item.completions.forEach { c ->
                            val startMin = minuteOfDay(c.plannedStart, date).coerceIn(0, maxMinutes)
                            val endMin = minuteOfDay(c.plannedEnd, date)
                                .coerceAtLeast(startMin + 1)
                                .coerceAtMost(maxMinutes)
                            if (endMin <= startMin) return@forEach
                            val left = startMin * minuteWidth
                            val blockWidth = (endMin - startMin) * minuteWidth
                            val blockTop = idx * (rowPx + rowGapPx) + (rowPx - rowPx) / 2f
                            drawRoundRect(
                                color = planColor,
                                topLeft = Offset(left, blockTop),
                                size = Size(blockWidth, rowPx),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                            )
                            // 高亮"现在"包住的任务：右边加 1px 红边
                            if (isViewingToday &&
                                c.status == com.wakeplan.app.domain.model.TaskStatus.IN_PROGRESS &&
                                now.isAfter(c.plannedStart) && now.isBefore(c.plannedEnd)) {
                                drawRoundRect(
                                    color = nowLineColor,
                                    topLeft = Offset(left, blockTop),
                                    size = Size(blockWidth, rowPx),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                                )
                            }
                        }
                    }

                    // 整点垂直网格（每 3 小时一条线，颜色更深）
                    for (h in 0..24 step 3) {
                        val x = h * 60 * minuteWidth
                        drawLine(
                            color = outlineColor.copy(alpha = if (h % 6 == 0) 0.5f else 0.2f),
                            start = Offset(x, 0f),
                            end = Offset(x, tlHeight),
                            strokeWidth = if (h % 6 == 0) 1.2f else 0.7f,
                        )
                    }

                    // "现在"指示：顶部 + 底部 6dp 红圆点 + 1.5px 红线 + 4px 25% 光晕
                    val nowMin = minuteOfDay(now, date).coerceIn(0, maxMinutes)
                    val nowX = nowMin * minuteWidth
                    if (isViewingToday) {
                        // 顶部 6dp 圆点
                        drawCircle(
                            color = nowLineColor,
                            radius = with(density) { 5.dp.toPx() },
                            center = Offset(nowX, -with(density) { 6.dp.toPx() }),
                        )
                        // 光晕
                        drawLine(
                            color = nowLineColor.copy(alpha = 0.25f),
                            start = Offset(nowX - 3f, 0f),
                            end = Offset(nowX - 3f, tlHeight),
                            strokeWidth = 6f,
                        )
                        drawLine(
                            color = nowLineColor.copy(alpha = 0.25f),
                            start = Offset(nowX + 3f, 0f),
                            end = Offset(nowX + 3f, tlHeight),
                            strokeWidth = 6f,
                        )
                        // 主线
                        drawLine(
                            color = nowLineColor,
                            start = Offset(nowX, 0f),
                            end = Offset(nowX, tlHeight),
                            strokeWidth = 1.5f,
                        )
                    }

                    // 底部整点刻度 + 文字
                    val ticks = listOf(0, 3, 6, 9, 12, 15, 18, 21, 24)
                    val tickMarkHeight = with(density) { 4.dp.toPx() }
                    ticks.forEach { h ->
                        val x = h * 60 * minuteWidth
                        val isMajor = h % 6 == 0
                        // 短 tick（major 长一点）
                        drawLine(
                            color = outlineColor.copy(alpha = if (isMajor) 0.7f else 0.3f),
                            start = Offset(x, tlHeight),
                            end = Offset(x, tlHeight + if (isMajor) tickMarkHeight else tickMarkHeight / 2f),
                            strokeWidth = 1f,
                        )
                        if (isMajor) {
                            // major 刻度文字（每 6 小时：00/06/12/18/24）
                            val text = if (h == 24) "24" else String.format("%02d:00", h)
                            val measured = textMeasurer.measure(
                                text = text,
                                style = tickLabelStyle,
                            )
                            val textX = if (h == 24) w - measured.size.width else x - measured.size.width / 2f
                            drawText(
                                textMeasurer = textMeasurer,
                                text = text,
                                topLeft = Offset(textX, tlHeight + tickMarkHeight + 2f),
                                style = tickLabelStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 左侧 plan 名称列 — 与 Canvas track 等高，每条 plan 一行
 * 用 4dp plan color 色点 + 计划名前 6 字（避免 label 跟右侧时间轨挤压）
 */
@Composable
private fun PlanLabelColumn(
    planItems: List<TodayPlanItem>,
    rowGap: androidx.compose.ui.unit.Dp,
    labelColumnWidth: androidx.compose.ui.unit.Dp,
    laneMinHeight: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        if (planItems.isEmpty()) {
            // 空态：24h 一行空白
            Box(modifier = Modifier.height(laneMinHeight))
        } else {
            planItems.forEach { item ->
                val planColor = parseHexColor(item.plan.colorHex)
                Row(
                    modifier = Modifier
                        .height(rowHeight)
                        .width(labelColumnWidth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(planColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.plan.title.take(6),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 当前时间 pill — 时间线右上角
 *
 * 圆角胶囊 + 红色 10% 淡底 + 红色文字（强调），表示"现在几点"
 */
@Composable
private fun NowTimePill(time: String, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(WpRadius.full))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(6.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

private fun minuteOfDay(dt: LocalDateTime, date: LocalDate): Int {
    // 不区分日期，只看时分（跨午夜段如果 dt 是次日，minuteOfDay > 24*60）
    val dayOffset = (dt.toLocalDate().toEpochDay() - date.toEpochDay()).toInt()
    return dt.hour * 60 + dt.minute + dayOffset * 24 * 60
}

// ====================================================================
// v0.4 扁平任务卡（按时间排，跨 plan 混合）
// ====================================================================

/**
 * 单个任务卡 — 1 行布局：
 *   [09:30-10:00] [●ces]  task title    [开始][×]
 *
 * 左侧 56dp 留给时间（mono 数字 + 透明边框感）
 * 中间是 plan 颜色点 + plan 短名（最多 8 字）
 * 右边任务标题（weight 1f，吃剩余空间）
 * 最右是 action 按钮（按状态切换）
 */
@Composable
private fun TodayTaskCard(
    item: TodayTaskItem,
    conflicts: List<TaskConflict>,
    highlighted: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val planColor = parseHexColor(item.plan.colorHex)
    val start = formatHm(item.completion.plannedStart)
    val end = formatHm(item.completion.plannedEnd)
    val timeText = "$start – $end"

    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
             else MaterialTheme.colorScheme.surface

    val status = item.completion.status
    val isInProgressOrDone = status == TaskStatus.IN_PROGRESS ||
                             status == TaskStatus.COMPLETED ||
                             status == TaskStatus.SKIPPED
    val isCompleted = status == TaskStatus.COMPLETED
    val isSkipped = status == TaskStatus.SKIPPED
    val isPending = status == TaskStatus.PENDING

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WpRadius.lg),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 时间 (mono 数字，固定 56dp 宽)
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(80.dp),
            )

            Spacer(Modifier.width(8.dp))

            // Plan 颜色点 + 短名
            Row(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(WpRadius.full))
                    .background(planColor.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(planColor),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = item.plan.title.take(8),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = planColor,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(10.dp))

            // 任务标题（吃剩余空间）
            Text(
                text = item.task.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isPending) FontWeight.Normal else FontWeight.Medium,
                    textDecoration = if (isCompleted || isSkipped) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                ),
                color = if (isCompleted || isSkipped) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Action 按钮
            Spacer(Modifier.width(8.dp))
            when (status) {
                TaskStatus.PENDING -> {
                    ActionChip(
                        icon = Icons.Outlined.PlayArrow,
                        label = stringResource(R.string.today_btn_start),
                        onClick = onStart,
                    )
                    Spacer(Modifier.width(4.dp))
                    IconActionButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.today_cd_skip),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onSkip,
                    )
                }
                TaskStatus.IN_PROGRESS -> {
                    ActionChip(
                        icon = Icons.Outlined.Check,
                        label = stringResource(R.string.today_btn_complete),
                        onClick = onComplete,
                        primary = true,
                    )
                    Spacer(Modifier.width(4.dp))
                    IconActionButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.today_cd_skip),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onSkip,
                    )
                }
                TaskStatus.COMPLETED, TaskStatus.SKIPPED -> {
                    IconActionButton(
                        icon = Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.today_cd_start),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onStart,
                    )
                }
            }
        }
    }

    // 冲突警告内联（这行的 plan 有冲突时）
    if (conflicts.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        // 复用现有的 ConflictBanner
        // （构造一个空 plan map —— 实际只有 taskA/taskB 的 planId，
        //  这里我们用 item.plan 单值近似。如果真冲突多 plan，
        //  ConflictBanner 会显示"其他计划"字样）
        ConflictBanner(
            conflicts = conflicts,
            plansById = mapOf(item.plan.id to item.plan),
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val containerColor = if (primary) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
    val contentColor = if (primary) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.primary
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(WpRadius.full),
        color = if (primary) containerColor
                else containerColor.copy(alpha = 0.0f),
        border = if (!primary) androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun IconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatHm(dt: java.time.LocalDateTime): String =
    String.format("%02d:%02d", dt.hour, dt.minute)

// ====================================================================
// 旧的 plan-grouped card（已废弃，留作 v0.3 回滚参考）
// ====================================================================
//
// @Composable
// private fun TodayPlanCard(...) { ... }  // v0.3 扁平前的实现
// @Composable
// private fun TaskRow(...) { ... }        // v0.3 扁平前的内联任务行

@Composable
private fun ConflictBanner(
    conflicts: List<TaskConflict>,
    plansById: Map<String, Plan>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val maxOverlap = conflicts.maxOf { it.overlapMinutes }
    val otherPlan = conflicts.firstOrNull()?.let { c ->
        plansById[c.taskA.planId]?.takeIf { it.id != c.taskB.planId }
            ?: plansById[c.taskB.planId]
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    R.string.today_conflict,
                    otherPlan?.title ?: stringResource(R.string.today_conflict_other_plan),
                    maxOverlap,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun EmptyTodayState(
    isViewingToday: Boolean,
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isViewingToday) {
                    stringResource(R.string.today_empty_title_today)
                } else {
                    stringResource(R.string.today_empty_title_other, date.format(dateFmt(context)))
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.today_empty_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// helper：把分钟数（0..1440，跨日可 >1440）格式化为 HH:mm
private fun formatMinutes(minutes: Int): String {
    val total = ((minutes % 1440) + 1440) % 1440  // 折叠到 0..1439
    val h = total / 60
    val m = total % 60
    return "%02d:%02d".format(h, m)
}

private fun dateFmt(context: android.content.Context): DateTimeFormatter =
    DateTimeFormatter.ofPattern(context.getString(R.string.today_date_pattern))
