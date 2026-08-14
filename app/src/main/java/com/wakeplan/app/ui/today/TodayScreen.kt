package com.wakeplan.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
// 24h 水平时间线 — v0.4 "Bold Edition" 第二次重做
// ====================================================================
// 关键变化（vs 上一版 per-plan lane）：
//   - 单条 track — 计划多了也不会拉高，所有 plan 的 task 都在一条线上
//   - 画布 24h 宽（2112dp），用户可水平拖动
//   - 默认视图把 now 滚到视口中央（用户看到 ±3h）
//   - 时间轴 + track 一起滚（同一 Column）
//   - 任务块 40dp 高，8dp 圆角；宽到 56dp+ 时画「起时间 + 任务名」白字
//   - 时间轴上加 4dp 红点标记 now（用户拖走后还能看到"现在"在轴上的位置）
//   - now 线 2px 主线 + 双侧 4dp 8px 18% 光晕

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
        fontWeight = FontWeight.Medium,
    )
    val nowLineColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onTrackColor = MaterialTheme.colorScheme.surface  // task 块内的白/底色

    // ---- 布局尺寸 ----
    val hourAxisHeight = 20.dp         // 顶部整点刻度行
    val axisToTrackGap = 6.dp
    val trackHeight = 56.dp            // 单条 track 高度
    val blockHeight = 40.dp            // task 色块高度
    val blockCornerRadius = 8.dp
    val pixelsPerHour = 88.dp          // 每小时占多少 dp（24h = 2112dp 总宽）
    val totalCanvasWidth = pixelsPerHour * 24
    val maxMinutes = 24 * 60
    val nowTimeText = String.format("%02d:%02d", now.hour, now.minute)

    val tasksById = remember(planItems) {
        planItems.flatMap { it.tasks }.associateBy { it.id }
    }
    val plansById = remember(planItems) {
        planItems.associate { it.plan.id to it.plan }
    }

    val titleText = if (isViewingToday) {
        stringResource(R.string.today_timeline_title_today)
    } else {
        stringResource(R.string.today_timeline_title_other, date.format(dateFmt(context)))
    }

    val scrollState = rememberScrollState()

    // 不再用 Surface card — 跟 page 背景融合
    Column(modifier = Modifier.fillMaxWidth()) {
        // ---- 头部行：标题 + 当前时间 pill ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isViewingToday) {
                NowTimePill(time = nowTimeText, color = nowLineColor)
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- 主体：水平滚动时间线 ----
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }.toInt()
            val totalWidthPx = with(density) { totalCanvasWidth.toPx() }.toInt()

            // 首次进入：把 now 滚到视口中央（让用户看到 ±3h）
            val initialTargetPx = if (isViewingToday) {
                val nowMin = now.hour * 60 + now.minute
                val nowX = (nowMin.toFloat() / maxMinutes) * totalWidthPx
                (nowX - viewportWidthPx / 2f).toInt().coerceAtLeast(0)
            } else 0
            var didInitialScroll by remember { mutableStateOf(false) }
            LaunchedEffect(viewportWidthPx) {
                if (isViewingToday && !didInitialScroll && initialTargetPx > 0) {
                    scrollState.scrollTo(initialTargetPx)
                    didInitialScroll = true
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
            ) {
                Column(
                    modifier = Modifier.width(totalCanvasWidth),
                ) {
                    // ---- 顶部 hour axis ----
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hourAxisHeight),
                    ) {
                        val w = size.width
                        val minuteWidth = w / maxMinutes.toFloat()
                        val tickBottomY = size.height
                        val ticks = listOf(0, 3, 6, 9, 12, 15, 18, 21, 24)
                        ticks.forEach { h ->
                            val x = h * 60 * minuteWidth
                            val isMajor = h % 6 == 0
                            val isMid = h % 3 == 0
                            val tickLen = when {
                                isMajor -> with(density) { 5.dp.toPx() }
                                isMid -> with(density) { 3.dp.toPx() }
                                else -> with(density) { 1.5.dp.toPx() }
                            }
                            drawLine(
                                color = outlineColor.copy(alpha = if (isMajor) 0.7f else 0.3f),
                                start = Offset(x, tickBottomY - tickLen),
                                end = Offset(x, tickBottomY),
                                strokeWidth = if (isMajor) 1.2f else 0.8f,
                            )
                            if (isMajor) {
                                val text = if (h == 24) "24" else String.format("%02d", h)
                                val measured = textMeasurer.measure(text, style = tickLabelStyle)
                                val textX = when (h) {
                                    0 -> 0f
                                    24 -> w - measured.size.width
                                    else -> x - measured.size.width / 2f
                                }
                                val textY = tickBottomY - tickLen - measured.size.height - 2f
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    topLeft = Offset(textX, textY),
                                    style = tickLabelStyle,
                                )
                            }
                        }

                        // now dot on axis — 拖走后还能看到"现在"在轴上的位置
                        if (isViewingToday) {
                            val nowMin = now.hour * 60 + now.minute
                            val nowX = (nowMin.toFloat() / maxMinutes) * w
                            val dotRadius = with(density) { 4.dp.toPx() }
                            // halo
                            drawCircle(
                                color = nowLineColor.copy(alpha = 0.18f),
                                radius = dotRadius + with(density) { 3.dp.toPx() },
                                center = Offset(nowX, tickBottomY - with(density) { 7.dp.toPx() }),
                            )
                            // dot
                            drawCircle(
                                color = nowLineColor,
                                radius = dotRadius,
                                center = Offset(nowX, tickBottomY - with(density) { 7.dp.toPx() }),
                            )
                        }
                    }
                    Spacer(Modifier.height(axisToTrackGap))

                    // ---- 单条 track canvas ----
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight),
                    ) {
                        val w = size.width
                        val tlHeight = trackHeight.toPx()
                        val minuteWidth = w / maxMinutes.toFloat()
                        val blockPx = blockHeight.toPx()
                        val cornerPx = with(density) { blockCornerRadius.toPx() }

                        // 1) 时间网格
                        for (h in 0..24) {
                            val x = h * 60 * minuteWidth
                            val isProminent = h == 0 || h == 24
                            val isMajor = h % 6 == 0
                            val isMid = h % 3 == 0
                            val (alpha, width) = when {
                                isProminent -> 0.55f to 1.2f
                                isMajor -> 0.4f to 1f
                                isMid -> 0.22f to 0.7f
                                else -> 0.10f to 0.4f
                            }
                            drawLine(
                                color = outlineColor.copy(alpha = alpha),
                                start = Offset(x, 0f),
                                end = Offset(x, tlHeight),
                                strokeWidth = width,
                            )
                        }

                        // 2) task 块（所有 plan 共享一条 track，按时间排）
                        val blockTextStyle = tickLabelStyle.copy(
                            color = onTrackColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val minTextWidth = with(density) { 56.dp.toPx() }
                        val blockPadding = with(density) { 7.dp.toPx() }
                        val blockGap = with(density) { 5.dp.toPx() }

                        val allCompletions = planItems.flatMap { it.completions }
                            .sortedBy { it.plannedStart }
                        allCompletions.forEach { c ->
                            val plan = plansById[c.planId] ?: return@forEach
                            val planColor = parseHexColor(plan.colorHex)

                            val startMin = minuteOfDay(c.plannedStart, date).coerceIn(0, maxMinutes)
                            val endMin = minuteOfDay(c.plannedEnd, date)
                                .coerceAtLeast(startMin + 1)
                                .coerceAtMost(maxMinutes)
                            if (endMin <= startMin) return@forEach
                            val left = startMin * minuteWidth
                            val blockWidth = (endMin - startMin) * minuteWidth
                            val blockTop = (tlHeight - blockPx) / 2f

                            // 主体（plan color 填充）
                            drawRoundRect(
                                color = planColor,
                                topLeft = Offset(left, blockTop),
                                size = Size(blockWidth, blockPx),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                            )

                            // in-progress：2px 红边
                            if (isViewingToday &&
                                c.status == TaskStatus.IN_PROGRESS &&
                                now.isAfter(c.plannedStart) && now.isBefore(c.plannedEnd)) {
                                drawRoundRect(
                                    color = nowLineColor,
                                    topLeft = Offset(left, blockTop),
                                    size = Size(blockWidth, blockPx),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                                )
                            }

                            // 文字：宽到 minTextWidth 时画「起时间」；再宽画「任务名」
                            if (blockWidth >= minTextWidth) {
                                val startText = String.format(
                                    "%02d:%02d",
                                    c.plannedStart.hour, c.plannedStart.minute,
                                )
                                val startMeasured = textMeasurer.measure(startText, style = blockTextStyle)
                                val centerY = blockTop + blockPx / 2f
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = startText,
                                    topLeft = Offset(
                                        left + blockPadding,
                                        centerY - startMeasured.size.height / 2f,
                                    ),
                                    style = blockTextStyle,
                                )

                                val titleSpace = blockWidth - blockPadding * 2 -
                                        startMeasured.size.width - blockGap
                                if (titleSpace > with(density) { 24.dp.toPx() }) {
                                    val taskTitle = tasksById[c.taskId]?.title ?: ""
                                    var shown = ""
                                    for (i in taskTitle.indices) {
                                        val candidate = taskTitle.substring(0, i + 1)
                                        val m = textMeasurer.measure(candidate, style = blockTextStyle)
                                        if (m.size.width.toFloat() > titleSpace) break
                                        shown = candidate
                                    }
                                    val finalText = if (shown.length < taskTitle.length && shown.isNotEmpty()) {
                                        shown.dropLast(1) + "…"
                                    } else if (shown.isEmpty() && taskTitle.isNotEmpty()) {
                                        "…"
                                    } else {
                                        shown
                                    }
                                    if (finalText.isNotEmpty()) {
                                        val titleMeasured = textMeasurer.measure(finalText, style = blockTextStyle)
                                        drawText(
                                            textMeasurer = textMeasurer,
                                            text = finalText,
                                            topLeft = Offset(
                                                left + blockPadding + startMeasured.size.width + blockGap,
                                                centerY - titleMeasured.size.height / 2f,
                                            ),
                                            style = blockTextStyle,
                                        )
                                    }
                                }
                            }
                        }

                        // 3) now 线（2px 主线 + 双侧 4dp 8px 18% 光晕）
                        if (isViewingToday) {
                            val nowMin = minuteOfDay(now, date).coerceIn(0, maxMinutes)
                            val nowX = (nowMin.toFloat() / maxMinutes) * w
                            val haloOffset = with(density) { 4.dp.toPx() }
                            val haloWidth = with(density) { 8.dp.toPx() }

                            drawLine(
                                color = nowLineColor.copy(alpha = 0.18f),
                                start = Offset(nowX - haloOffset, 0f),
                                end = Offset(nowX - haloOffset, tlHeight),
                                strokeWidth = haloWidth,
                            )
                            drawLine(
                                color = nowLineColor.copy(alpha = 0.18f),
                                start = Offset(nowX + haloOffset, 0f),
                                end = Offset(nowX + haloOffset, tlHeight),
                                strokeWidth = haloWidth,
                            )
                            drawLine(
                                color = nowLineColor,
                                start = Offset(nowX, 0f),
                                end = Offset(nowX, tlHeight),
                                strokeWidth = 2f,
                            )
                        }
                    }
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
