package com.wakeplan.app.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.ui.common.WpAnimateInContainer
import com.wakeplan.app.ui.common.WpRadius
import com.wakeplan.app.ui.common.parseHexColor

@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onEditPlan: () -> Unit = {},
    onAddTask: () -> Unit,
    onTaskClick: (taskId: String) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val todayConflicts by viewModel.todayConflicts.collectAsStateWithLifecycle()
    var showDeletePlanDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val currentPlan = plan
    if (currentPlan == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val planColor = remember(currentPlan.colorHex) {
        runCatching { parseHexColor(currentPlan.colorHex) }.getOrElse { Color(0xFFC48520) }
    }
    val planTypeLabel = derivePlanTypeLabel(currentPlan.title)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 100.dp,
                start = 20.dp,
                end = 20.dp,
            ),
        ) {
            item("status_bar_space") {
                Spacer(Modifier.height(44.dp))
            }

            item("nav_bar") {
                DetailNavBar(
                    planTitle = currentPlan.title,
                    planColor = planColor,
                    planTypeLabel = planTypeLabel,
                    onBack = onBack,
                    onMenuClick = { showMenu = true },
                    showMenu = showMenu,
                    onDismissMenu = { showMenu = false },
                    onEdit = {
                        showMenu = false
                        onEditPlan()
                    },
                    onDelete = {
                        showMenu = false
                        showDeletePlanDialog = true
                    },
                    onToggleArchive = {
                        showMenu = false
                        viewModel.setArchived(!currentPlan.archived, onDone = onBack)
                    },
                    isArchived = currentPlan.archived,
                )
            }

            item("header_padding") {
                Spacer(Modifier.height(16.dp))
            }

            item("stats_bar") {
                WpAnimateInContainer(index = 0) {
                    StatsBar(
                        todayCompleted = stats.todayCompleted,
                        weekCompleted = stats.weekCompleted,
                        totalCompleted = stats.totalCompleted,
                    )
                }
            }

            item("section_title") {
                SectionTitle(stringResource(R.string.plan_detail_section_schedule))
            }

            if (tasks.isEmpty()) {
                item("empty") {
                    WpAnimateInContainer(index = 1) {
                        EmptyTaskState()
                    }
                }
            } else {
                itemsIndexed(tasks, key = { _, t -> t.id }) { index, task ->
                    WpAnimateInContainer(index = index + 1) {
                        Spacer(Modifier.height(if (index == 0) 0.dp else 8.dp))
                        PlanTaskItem(
                            task = task,
                            accent = planColor,
                            onClick = { onTaskClick(task.id) },
                            conflictMinutes = todayConflicts[task.id]?.overlapMinutes,
                        )
                    }
                }
            }

            item("add_task") {
                Spacer(Modifier.height(8.dp))
                AddTaskButton(onClick = onAddTask)
            }
        }
    }

    if (showDeletePlanDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePlanDialog = false },
            title = { Text(stringResource(R.string.plan_detail_delete_dialog_title)) },
            text = { Text(stringResource(R.string.plan_detail_delete_dialog_text, currentPlan.title)) },
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
private fun DetailNavBar(
    planTitle: String,
    planColor: Color,
    planTypeLabel: String,
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleArchive: () -> Unit,
    isArchived: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavCircleButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlanBadge(label = planTypeLabel, color = planColor)
            Spacer(Modifier.height(2.dp))
            Text(
                text = planTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(8.dp))

        Box {
            NavCircleButton(onClick = onMenuClick) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.plan_detail_cd_more),
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = onDismissMenu,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_detail_edit)) },
                    onClick = onEdit,
                    leadingIcon = {
                        Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(18.dp))
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isArchived) R.string.plan_unarchive else R.string.plan_archive
                            )
                        )
                    },
                    onClick = onToggleArchive,
                    leadingIcon = {
                        Icon(
                            if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = null,
                            Modifier.size(18.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = onDelete,
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    },
                )
            }
        }
    }
}

@Composable
private fun NavCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PlanBadge(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(WpRadius.full))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = color,
        )
    }
}

@Composable
private fun StatsBar(
    todayCompleted: Int,
    weekCompleted: Int,
    totalCompleted: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            value = todayCompleted.toString(),
            label = stringResource(R.string.plan_detail_today_completed),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = weekCompleted.toString(),
            label = stringResource(R.string.plan_detail_week_completed),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = totalCompleted.toString(),
            label = stringResource(R.string.plan_detail_total_completed),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                ),
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        ),
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 14.dp, end = 14.dp),
    )
}

@Composable
private fun PlanTaskItem(
    task: Task,
    accent: Color,
    onClick: () -> Unit,
    conflictMinutes: Int? = null,
) {
    val isCompleted = false
    val completedAlpha = if (isCompleted) 0.5f else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(completedAlpha)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeBlock(
                startMinutes = task.startMinutes,
                endMinutes = task.endMinutes,
                modifier = Modifier.alpha(if (isCompleted) 0.5f else 1f),
            )

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .background(accent, RoundedCornerShape(WpRadius.full)),
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                    )
                    if (task.isLocked) {
                        LockBadge(accent = accent)
                    }
                }

                val repeatLabel = repeatRuleLabel(task)
                if (repeatLabel != null) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(WpRadius.full))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = repeatLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                // v0.4 内联冲突标注（PRD US-09：不弹 modal）
                if (conflictMinutes != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.plan_detail_conflict, conflictMinutes),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TimeBlock(
    startMinutes: Int,
    endMinutes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatTimeShort(startMinutes),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        DashedVerticalDivider()
        Text(
            text = formatTimeShort(endMinutes) + if (endMinutes >= 1440) " +1" else "",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun DashedVerticalDivider() {
    Canvas(modifier = Modifier.width(1.dp).height(12.dp)) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
        drawLine(
            color = Color.Gray.copy(alpha = 0.4f),
            start = Offset(0f, 0f),
            end = Offset(0f, size.height),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = pathEffect,
        )
    }
}

@Composable
private fun LockBadge(accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(WpRadius.sm))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = stringResource(R.string.common_pinned),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = accent,
        )
    }
}

@Composable
private fun AddTaskButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(WpRadius.xl)
    val borderColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .dashedBorder(
                width = 1.5.dp,
                color = borderColor,
                cornerRadius = WpRadius.xl,
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.plan_detail_add_task),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = primaryColor,
            )
        }
    }
}

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
): Modifier = this.drawWithCache {
    val pxWidth = width.toPx()
    val pxCorner = cornerRadius.toPx()
    val stroke = Stroke(
        width = pxWidth,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(12f, 8f),
            0f,
        ),
    )
    onDrawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            topLeft = Offset(pxWidth / 2, pxWidth / 2),
            size = Size(size.width - pxWidth, size.height - pxWidth),
            cornerRadius = CornerRadius(pxCorner, pxCorner),
            style = stroke,
        )
    }
}

@Composable
private fun EmptyTaskState() {
    Surface(
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.plan_detail_no_tasks),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatTimeShort(minutes: Int): String {
    val total = ((minutes % 1440) + 1440) % 1440
    val h = total / 60
    val m = total % 60
    return "%02d:%02d".format(h, m)
}

@Composable
private fun repeatRuleLabel(task: Task): String? = when (task.repeatRule) {
    RepeatRule.ONCE -> null
    RepeatRule.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    RepeatRule.DAILY -> stringResource(R.string.repeat_daily)
    RepeatRule.CUSTOM -> if (task.customDays.isNotEmpty()) {
        stringResource(R.string.repeat_custom)
    } else null
}

@Composable
private fun derivePlanTypeLabel(title: String): String {
    return when {
        title.contains("健") || title.contains("运动") || title.contains("健身") ->
            stringResource(R.string.plan_type_fitness)
        title.contains("学") || title.contains("读") ->
            stringResource(R.string.plan_type_study)
        title.contains("写") || title.contains("创作") ->
            stringResource(R.string.plan_type_writing)
        title.contains("副业") || title.contains("项目") ->
            stringResource(R.string.plan_type_side)
        else -> title.take(2)
    }
}
