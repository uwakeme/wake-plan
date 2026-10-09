package com.wakeplan.app.ui.today

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.usecase.TaskConflict
import com.wakeplan.app.ui.common.HighlightBus
import com.wakeplan.app.ui.common.WpAnimateInContainer
import com.wakeplan.app.ui.common.WpRadius
import com.wakeplan.app.ui.common.parseHexColor
import com.wakeplan.app.ui.theme.WpSuccessDark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Composable
fun TodayScreen() {
    val viewModel: TodayViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val highlightedTaskId by HighlightBus.highlightedTaskId.collectAsStateWithLifecycle()
    val undoableAction by viewModel.undoableAction.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.ensureTodayCompletions()
    }

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
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undo()
            SnackbarResult.Dismissed -> viewModel.consumeUndo()
        }
    }

    val isViewingToday by viewModel.isViewingToday.collectAsStateWithLifecycle()
    var showQuickAdd by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> WpSnackbar(data) } },
        containerColor = MaterialTheme.colorScheme.background,
        // 布局自己处理系统 insets，Scaffold 侧置零（内层统一 padding）
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            // v0.4 快速录入：只在"今天"视图提供（非今天看历史时加任务没有意义）
            if (isViewingToday) {
                FloatingActionButton(
                    onClick = { showQuickAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.today_quick_add_title),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (state.planItems.isEmpty()) {
                EmptyTodayState(
                    isViewingToday = isViewingToday,
                    date = state.date,
                )
            } else {
                TodayContent(
                    state = state,
                    isViewingToday = isViewingToday,
                    listState = listState,
                    highlightedTaskId = highlightedTaskId,
                    onStartTask = viewModel::onStartTask,
                    onCompleteTask = viewModel::onCompleteTask,
                    onSkipTask = viewModel::onSkipTask,
                )
            }
        }
    }

    if (showQuickAdd) {
        QuickAddTaskSheet(
            plans = state.planItems.map { it.plan },
            defaultPlanId = state.planItems.firstOrNull()?.plan?.id,
            onSave = { planId, title, start, end ->
                viewModel.quickAddTask(planId, title, start, end)
            },
            onDismiss = { showQuickAdd = false },
        )
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    isViewingToday: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    highlightedTaskId: String?,
    onStartTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onSkipTask: (String) -> Unit,
) {
    val currentTask = state.todayTaskItems.firstOrNull { item ->
        item.completion.status == TaskStatus.IN_PROGRESS
    } ?: state.todayTaskItems.firstOrNull { item ->
        // PENDING 且开始时间在过去 30 分钟内（已经"应开始"但还没开始）
        item.completion.status == TaskStatus.PENDING &&
                !item.completion.plannedStart.isAfter(state.now) &&
                item.completion.plannedStart.isAfter(state.now.minusMinutes(30))
    }

    val pendingTasks = state.todayTaskItems.filter { it.completion.status == TaskStatus.PENDING }
    val completedTasks = state.todayTaskItems.filter {
        it.completion.status == TaskStatus.COMPLETED || it.completion.status == TaskStatus.SKIPPED
    }
    val inProgressOrCurrent = state.todayTaskItems.filter {
        it.completion.status == TaskStatus.IN_PROGRESS
    }

    // 深链滚动：key 列表与下方 LazyColumn 结构一一对应，按 taskId 定位真实列表下标。
    // v0.4 扁平化后旧逻辑还按 plan 索引 +2 猜位置，基本滚不到位
    LaunchedEffect(highlightedTaskId, state.todayTaskItems) {
        val targetId = highlightedTaskId ?: return@LaunchedEffect
        val keys = mutableListOf<String>("header", "now-card", "timeline")
        var targetKey: String? = null
        if (inProgressOrCurrent.isNotEmpty()) {
            keys += "section-in-progress"
            inProgressOrCurrent.forEach { item ->
                val key = "task-ip-${item.completion.id}"
                if (item.completion.taskId == targetId) targetKey = key
                keys += key
            }
        }
        if (pendingTasks.isNotEmpty()) {
            keys += "section-upcoming"
            pendingTasks.forEach { item ->
                val key = "task-pending-${item.completion.id}"
                if (item.completion.taskId == targetId) targetKey = key
                keys += key
            }
        }
        if (completedTasks.isNotEmpty()) {
            keys += "section-completed"
            completedTasks.forEach { item ->
                val key = "task-done-${item.completion.id}"
                if (item.completion.taskId == targetId) targetKey = key
                keys += key
            }
        }
        val index = targetKey?.let(keys::indexOf) ?: -1
        if (index >= 0) {
            listState.animateScrollToItem(index)
            delay(5_000L)
        }
        // 目标不在列表里也要清掉，避免高亮 id 滞留在单例里
        HighlightBus.clear()
    }

    val plansById = state.planItems.associate { it.plan.id to it.plan }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 100.dp,
            start = 0.dp,
            end = 0.dp,
        ),
    ) {
        item(key = "header") {
            TodayHeader(date = state.date, now = state.now, isViewingToday = isViewingToday)
        }

        item(key = "now-card") {
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                NowCard(
                    currentTask = currentTask,
                    now = state.now,
                    isViewingToday = isViewingToday,
                    onComplete = currentTask?.let { { onCompleteTask(it.completion.id) } },
                    onSkip = currentTask?.let { { onSkipTask(it.completion.id) } },
                    onStart = currentTask?.completion?.status?.let { status ->
                        if (status == TaskStatus.PENDING) {
                            { onStartTask(currentTask.completion.id) }
                        } else null
                    },
                )
            }
        }

        item(key = "timeline") {
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                TodayTimeLine(
                    date = state.date,
                    isViewingToday = isViewingToday,
                    now = state.now,
                    planItems = state.planItems,
                    conflicts = state.conflicts,
                )
            }
        }

        if (inProgressOrCurrent.isNotEmpty()) {
            item(key = "section-in-progress") {
                Spacer(Modifier.height(24.dp))
                SectionHeader(title = stringResource(R.string.today_section_in_progress))
            }
            itemsIndexed(inProgressOrCurrent, key = { _, item -> "task-ip-${item.completion.id}" }) { index, item ->
                // 按 task 匹配冲突——之前按 plan 匹配，同计划其他任务的冲突
                // 也会挂到这张卡上
                val taskConflicts = state.conflicts.filter { c ->
                    c.taskA.taskId == item.completion.taskId || c.taskB.taskId == item.completion.taskId
                }
                Spacer(Modifier.height(if (index == 0) 12.dp else 0.dp))
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    WpAnimateInContainer(index = index) {
                        TodayTaskCard(
                            item = item,
                            conflicts = taskConflicts,
                            plansById = plansById,
                            highlighted = item.completion.taskId == highlightedTaskId,
                            onStart = { onStartTask(item.completion.id) },
                            onComplete = { onCompleteTask(item.completion.id) },
                            onSkip = { onSkipTask(item.completion.id) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (pendingTasks.isNotEmpty()) {
            item(key = "section-upcoming") {
                Spacer(Modifier.height(24.dp))
                SectionHeader(title = stringResource(R.string.today_section_upcoming))
            }
            itemsIndexed(pendingTasks, key = { _, item -> "task-pending-${item.completion.id}" }) { index, item ->
                val taskConflicts = state.conflicts.filter { c ->
                    c.taskA.taskId == item.completion.taskId || c.taskB.taskId == item.completion.taskId
                }
                Spacer(Modifier.height(if (index == 0) 12.dp else 0.dp))
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    WpAnimateInContainer(index = index + inProgressOrCurrent.size) {
                        TodayTaskCard(
                            item = item,
                            conflicts = taskConflicts,
                            plansById = plansById,
                            highlighted = item.completion.taskId == highlightedTaskId,
                            onStart = { onStartTask(item.completion.id) },
                            onComplete = { onCompleteTask(item.completion.id) },
                            onSkip = { onSkipTask(item.completion.id) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (completedTasks.isNotEmpty()) {
            item(key = "section-completed") {
                Spacer(Modifier.height(24.dp))
                SectionHeader(title = stringResource(R.string.today_section_completed))
            }
            itemsIndexed(completedTasks, key = { _, item -> "task-done-${item.completion.id}" }) { index, item ->
                Spacer(Modifier.height(if (index == 0) 12.dp else 0.dp))
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    TodayTaskCard(
                        item = item,
                        conflicts = emptyList(),
                        plansById = plansById,
                        highlighted = false,
                        onStart = { onStartTask(item.completion.id) },
                        onComplete = { onCompleteTask(item.completion.id) },
                        onSkip = { onSkipTask(item.completion.id) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TodayHeader(date: LocalDate, now: LocalDateTime, isViewingToday: Boolean) {
    // java.time 的 SHORT 样式：zh → 周一，en → Mon，跟随 App locale
    val dayOfWeek = date.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT,
        java.util.Locale.getDefault(),
    )
    val timeStr = String.format("%02d:%02d", now.hour, now.minute)
    val dateStr = "$dayOfWeek · ${date.format(dateFmt())}"
    val metaStr = if (isViewingToday) "$dateStr · $timeStr" else dateStr

    val greeting = when {
        isViewingToday -> {
            val hour = now.hour
            when {
                hour < 6 -> stringResource(R.string.today_greeting_night)
                hour < 12 -> stringResource(R.string.today_greeting_morning)
                hour < 14 -> stringResource(R.string.today_greeting_noon)
                hour < 18 -> stringResource(R.string.today_greeting_afternoon)
                else -> stringResource(R.string.today_greeting_evening)
            }
        }
        else -> stringResource(R.string.today_greeting_other_date, date.format(dateFmt()))
    }

    val highlightWord = stringResource(R.string.today_greeting_word)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = metaStr,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.05.sp,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(4.dp))
        val highlightStart = greeting.indexOf(highlightWord)
        if (highlightStart >= 0) {
            val before = greeting.substring(0, highlightStart)
            val after = greeting.substring(highlightStart + highlightWord.length)
            Row {
                Text(
                    text = before,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.04).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = highlightWord,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.04).sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = after,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.04).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            Text(
                text = greeting,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.04).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.01).sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
    )
}

@Composable
private fun NowCard(
    currentTask: TodayTaskItem?,
    now: LocalDateTime,
    isViewingToday: Boolean,
    onComplete: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    onStart: (() -> Unit)?,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(28.dp),
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E2A45),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-60).dp)
                .size(200.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            if (currentTask != null && isViewingToday) {
                val isInProgress = currentTask.completion.status == TaskStatus.IN_PROGRESS
                val planColor = parseHexColor(currentTask.plan.colorHex)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha),
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isInProgress) stringResource(R.string.today_status_in_progress)
                        else stringResource(R.string.today_status_starting_soon),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = currentTask.task.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(WpRadius.full))
                            .background(planColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(planColor),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = currentTask.plan.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = planColor,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                val start = currentTask.completion.plannedStart
                val end = currentTask.completion.plannedEnd
                val startTime = formatHm(start)
                val endTime = formatHm(end)
                val totalMinutes = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(1)
                val elapsedMinutes = if (isInProgress) {
                    java.time.Duration.between(start, now).toMinutes().coerceIn(0, totalMinutes)
                } else 0
                val remainingMinutes = (totalMinutes - elapsedMinutes).coerceAtLeast(0)
                val progress = if (totalMinutes > 0) elapsedMinutes.toFloat() / totalMinutes else 0f
                val remainingText = when {
                    remainingMinutes >= 60 -> stringResource(
                        R.string.today_duration_hm, remainingMinutes / 60, remainingMinutes % 60,
                    )
                    remainingMinutes > 0 -> stringResource(R.string.today_duration_m, remainingMinutes)
                    else -> stringResource(R.string.today_remaining_soon)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$startTime – $endTime",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Text(
                        text = stringResource(R.string.today_remaining_label, remainingText),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    ),
                                ),
                            ),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val actionLabel = when {
                        isInProgress -> stringResource(R.string.today_btn_complete)
                        onStart != null -> stringResource(R.string.today_btn_start)
                        else -> stringResource(R.string.today_btn_complete)
                    }
                    val actionCallback = when {
                        isInProgress -> onComplete
                        onStart != null -> onStart
                        else -> onComplete
                    }
                    val actionIcon = when {
                        isInProgress -> Icons.Outlined.Check
                        onStart != null -> Icons.Outlined.PlayArrow
                        else -> Icons.Outlined.Check
                    }
                    val actionButtonColor = when {
                        isInProgress -> WpSuccessDark
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        onClick = { actionCallback?.invoke() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = actionButtonColor,
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = Color(0xFF090E1A),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = actionLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF090E1A),
                            )
                        }
                    }

                    Surface(
                        onClick = { onSkip?.invoke() },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.today_cd_skip),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = if (isViewingToday) stringResource(R.string.today_nowcard_next_up)
                    else stringResource(R.string.today_nowcard_no_task),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isViewingToday) stringResource(R.string.today_nowcard_free_slot)
                    else stringResource(R.string.today_nowcard_pick_date),
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}


@Composable
private fun TodayTimeLine(
    date: LocalDate,
    isViewingToday: Boolean,
    now: LocalDateTime,
    planItems: List<TodayPlanItem>,
    conflicts: List<TaskConflict>,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tickLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    )
    val nowLineColor = Color(0xFFE85B56)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onTrackColor = Color.White

    val hourAxisHeight = 20.dp
    val axisToTrackGap = 6.dp
    val trackHeight = 56.dp
    val blockHeight = 40.dp
    val blockCornerRadius = 8.dp
    val pixelsPerHour = 88.dp
    val totalCanvasWidth = pixelsPerHour * 24
    val maxMinutes = 24 * 60

    val tasksById = remember(planItems) {
        planItems.flatMap { it.tasks }.associateBy { it.id }
    }
    val plansById = remember(planItems) {
        planItems.associate { it.plan.id to it.plan }
    }

    val titleText = if (isViewingToday) {
        stringResource(R.string.today_timeline_title_today)
    } else {
        stringResource(R.string.today_timeline_title_other, date.format(dateFmt()))
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = titleText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WpRadius.lg))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(WpRadius.lg),
                )
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                val viewportWidthPx = with(density) { maxWidth.toPx() }.toInt()
                val totalWidthPx = with(density) { totalCanvasWidth.toPx() }.toInt()

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
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(hourAxisHeight),
                        ) {
                            val w = size.width
                            val minuteWidth = w / maxMinutes.toFloat()
                            val tickBottomY = size.height
                            val ticks = (0..22 step 2).toList()
                            ticks.forEach { h ->
                                val x = h * 60 * minuteWidth
                                val isMajor = h % 4 == 0
                                val tickLen = when {
                                    isMajor -> with(density) { 5.dp.toPx() }
                                    else -> with(density) { 3.dp.toPx() }
                                }
                                drawLine(
                                    color = outlineColor.copy(alpha = if (isMajor) 0.5f else 0.25f),
                                    start = Offset(x, tickBottomY - tickLen),
                                    end = Offset(x, tickBottomY),
                                    strokeWidth = if (isMajor) 1.0f else 0.7f,
                                )
                                val text = String.format("%02d", h)
                                val measured = textMeasurer.measure(text, style = tickLabelStyle)
                                val textX = if (h == 0) 0f else x - measured.size.width / 2f
                                val textY = tickBottomY - tickLen - measured.size.height - 2f
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    topLeft = Offset(textX, textY),
                                    style = tickLabelStyle.copy(
                                        fontWeight = if (isMajor) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                )
                            }

                            if (isViewingToday) {
                                val nowMin = now.hour * 60 + now.minute
                                val nowX = (nowMin.toFloat() / maxMinutes) * w
                                val dotRadius = with(density) { 4.dp.toPx() }
                                drawCircle(
                                    color = nowLineColor.copy(alpha = 0.18f),
                                    radius = dotRadius + with(density) { 3.dp.toPx() },
                                    center = Offset(nowX, tickBottomY - with(density) { 7.dp.toPx() }),
                                )
                                drawCircle(
                                    color = nowLineColor,
                                    radius = dotRadius,
                                    center = Offset(nowX, tickBottomY - with(density) { 7.dp.toPx() }),
                                )
                            }
                        }
                        Spacer(Modifier.height(axisToTrackGap))

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

                            for (h in 0..22 step 2) {
                                val x = h * 60 * minuteWidth
                                val isProminent = h == 0 || h == 22
                                val isMajor = h % 4 == 0
                                val (alpha, width) = when {
                                    isProminent -> 0.35f to 1.0f
                                    isMajor -> 0.22f to 0.8f
                                    else -> 0.12f to 0.5f
                                }
                                drawLine(
                                    color = outlineColor.copy(alpha = alpha),
                                    start = Offset(x, 0f),
                                    end = Offset(x, tlHeight),
                                    strokeWidth = width,
                                )
                            }

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

                                // 跨日的 completion：plannedEnd 落到 date+1（如 23:00→次日 01:00）
                                // 这种任务"实际还在今天"，仍然在今天时间线画；plannedEnd 跨到 date+2
                                // （如 23:00→次日 23:00，应是结束于"后天"）则整个属于后天，不画
                                // 数据异常保护：plannedEnd 在 date+1 但 >= 12:00（如次日 09:00），
                                // 通常是 picker 把当日时间误转成跨日的产物，不画
                                val endDate = c.plannedEnd.toLocalDate()
                                if (endDate.isAfter(date.plusDays(1))) return@forEach
                                if (endDate == date.plusDays(1) && c.plannedEnd.hour >= 12) return@forEach

                                val startMin = minuteOfDay(c.plannedStart, date).coerceIn(0, maxMinutes)
                                val endMin = minuteOfDay(c.plannedEnd, date)
                                    .coerceAtLeast(startMin + 1)
                                    .coerceAtMost(maxMinutes)
                                if (endMin <= startMin) return@forEach
                                val left = startMin * minuteWidth
                                val blockWidth = (endMin - startMin) * minuteWidth
                                val blockTop = (tlHeight - blockPx) / 2f

                                drawRoundRect(
                                    color = planColor,
                                    topLeft = Offset(left, blockTop),
                                    size = Size(blockWidth, blockPx),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                                )

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

                            if (isViewingToday) {
                                val nowMin = minuteOfDay(now, date).coerceIn(0, maxMinutes)
                                val nowX = (nowMin.toFloat() / maxMinutes) * w
                                val lineHeight = with(density) { 40.dp.toPx() }
                                val lineTop = (tlHeight - lineHeight) / 2f
                                val pillWidth = with(density) { 12.dp.toPx() }

                                drawRoundRect(
                                    color = nowLineColor.copy(alpha = 0.15f),
                                    topLeft = Offset(nowX - pillWidth / 2f, lineTop),
                                    size = Size(pillWidth, lineHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillWidth / 2f, pillWidth / 2f),
                                )
                                drawLine(
                                    color = nowLineColor,
                                    start = Offset(nowX, lineTop),
                                    end = Offset(nowX, lineTop + lineHeight),
                                    strokeWidth = 2f,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun minuteOfDay(dt: LocalDateTime, date: LocalDate): Int {
    val dayOffset = (dt.toLocalDate().toEpochDay() - date.toEpochDay()).toInt()
    return dt.hour * 60 + dt.minute + dayOffset * 24 * 60
}

@Composable
private fun TodayTaskCard(
    item: TodayTaskItem,
    conflicts: List<TaskConflict>,
    plansById: Map<String, Plan>,
    highlighted: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val start = formatHm(item.completion.plannedStart)
    val end = formatHm(item.completion.plannedEnd)
    val timeText = "$start – $end"

    val status = item.completion.status
    val isCompleted = status == TaskStatus.COMPLETED || status == TaskStatus.SKIPPED
    val isInProgress = status == TaskStatus.IN_PROGRESS
    val isPending = status == TaskStatus.PENDING

    val defaultColor = MaterialTheme.colorScheme.primary
    val planColor = remember(item.plan.colorHex) {
        runCatching { parseHexColor(item.plan.colorHex) }.getOrElse { defaultColor }
    }

    val statusDotColor = when {
        isInProgress -> Color(0xFFE85B56)
        isPending -> planColor
        else -> MaterialTheme.colorScheme.tertiary
    }
    val timeTextColor = when {
        isInProgress -> Color(0xFFE85B56)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        isInProgress -> Color(0xFFE85B56).copy(alpha = 0.2f)
        highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val alphaMod = if (isCompleted) 0.5f else 1f

    val bgBrush = when {
        isInProgress -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFE85B56).copy(alpha = 0.08f),
                MaterialTheme.colorScheme.surface,
            )
        )
        highlighted -> Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                MaterialTheme.colorScheme.surface,
            )
        )
        else -> null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(WpRadius.xl),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = borderColor,
        ),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .then(if (bgBrush != null) Modifier.background(bgBrush) else Modifier.background(MaterialTheme.colorScheme.surface))
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = timeText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = timeTextColor.copy(alpha = alphaMod),
                    modifier = Modifier.width(96.dp),
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor.copy(alpha = alphaMod)),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alphaMod),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.plan.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = alphaMod),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                when (status) {
                    TaskStatus.PENDING -> {
                        Surface(
                            onClick = onStart,
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = stringResource(R.string.today_cd_start),
                                    tint = Color(0xFF090E1A),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    TaskStatus.IN_PROGRESS -> {
                        Surface(
                            onClick = onComplete,
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = WpSuccessDark,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.today_btn_complete),
                                    tint = Color(0xFF090E1A),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    TaskStatus.COMPLETED, TaskStatus.SKIPPED -> {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (conflicts.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        ConflictBanner(
            conflicts = conflicts,
            // 全量 plan 映射——之前只装本计划，"与 XX 计划冲突"显示的对方
            // 计划名永远解析成自己
            plansById = plansById,
        )
    }
}

@Composable
private fun ConflictBanner(
    conflicts: List<TaskConflict>,
    plansById: Map<String, Plan>,
) {
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
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .padding(top = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isViewingToday) {
                    stringResource(R.string.today_empty_title_today)
                } else {
                    stringResource(R.string.today_empty_title_other, date.format(dateFmt()))
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

private fun formatHm(dt: LocalDateTime): String =
    String.format("%02d:%02d", dt.hour, dt.minute)

/** 日期 pattern 走 strings 资源（zh "M月d日" / en "MMM d"），跟随 App 语言 */
@Composable
private fun dateFmt(): DateTimeFormatter =
    DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_md))

/**
 * 暗色主题下的 Snackbar：保持与卡片一致的圆角、深色背景、primary 色的 action 文字。
 * 默认 M3 Snackbar 走 inverseSurface（浅灰），跟暗色 UI 冲突。
 */
@Composable
private fun WpSnackbar(data: SnackbarData) {
    val actionLabel = data.visuals.actionLabel
    val showDismiss = data.visuals.withDismissAction
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (actionLabel != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { data.performAction() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp,
                    ),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (showDismiss) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { data.dismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
