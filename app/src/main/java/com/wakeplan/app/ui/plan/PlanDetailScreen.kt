package com.wakeplan.app.ui.plan

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.ui.common.TopHeaderContent
import com.wakeplan.app.ui.common.WpAnimateInContainer
import com.wakeplan.app.ui.common.WpRadius
import com.wakeplan.app.ui.common.WpShadowElevation
import com.wakeplan.app.ui.common.WpSpacing
import com.wakeplan.app.ui.common.parseHexColor
import com.wakeplan.app.ui.common.wpCard

/**
 * 计划详情屏 — v0.4 视觉刷新
 *
 * 视觉规范（来自 design/colors_and_type.css + superdesign 验证）：
 * - 顶部 TopAppBar：标题（plan.title）+ 副标（起始日期 + 状态）
 * - 计划头部卡：24dp 圆角，40dp 彩色 icon + 标题 + stats（任务数 / 时长）
 * - 任务行：12dp 圆角，左侧小色点 + 标题 + 时段 + repeat label
 * - FAB：跳新建任务
 * - 删除 dialog 保留
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onTaskClick: (taskId: String) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var showDeletePlanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TopHeaderContent(
                        title = plan?.title ?: stringResource(R.string.plan_detail_title_fallback),
                        subtitle = plan?.let {
                            stringResource(
                                R.string.plan_detail_header_subtitle,
                                it.startDate.toString(),
                                stringResource(if (it.isActive) R.string.plan_detail_status_active else R.string.plan_detail_status_archived),
                            )
                        },
                    )
                },
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
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.plan_detail_cd_add_task))
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
            tasks = tasks,
            contentPadding = padding,
            onTaskClick = onTaskClick,
            onDeleteTask = viewModel::deleteTask,
        )
    }

    if (showDeletePlanDialog && plan != null) {
        AlertDialog(
            onDismissRequest = { showDeletePlanDialog = false },
            title = { Text(stringResource(R.string.plan_detail_delete_dialog_title)) },
            text = { Text(stringResource(R.string.plan_detail_delete_dialog_text, plan!!.title)) },
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
    tasks: List<Task>,
    contentPadding: PaddingValues,
    onTaskClick: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    val planColor = remember(plan.colorHex) {
        runCatching { parseHexColor(plan.colorHex) }.getOrElse { Color(0xFFC48520) }
    }
    val iconKey = remember(plan.title) { PlanIconKey.derive(plan.title) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + WpSpacing.sm,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = WpSpacing.lg,
            end = WpSpacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(WpSpacing.md),
    ) {
        item("header") {
            WpAnimateInContainer(index = 0) {
                PlanHeaderCard(plan = plan, color = planColor, iconKey = iconKey, taskCount = tasks.size)
            }
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
                    TaskRow(
                        task = task,
                        accent = planColor,
                        onClick = { onTaskClick(task.id) },
                        onDelete = { onDeleteTask(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanHeaderCard(
    plan: Plan,
    color: Color,
    iconKey: PlanIconKey,
    taskCount: Int,
) {
    Surface(
        shape = RoundedCornerShape(WpRadius.xxl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(WpSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 40dp 彩色 icon 方块
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(WpRadius.lg))
                    .background(color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconKey.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(WpSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.plan_detail_task_count, taskCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    accent: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(WpRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WpSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧 8dp 色点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
            Spacer(Modifier.width(WpSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatTime(task.startMinutes)} – ${formatTime(task.endMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val repeatLabel = repeatRuleLabel(task)
                if (repeatLabel != null || task.isLocked) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            task.isLocked -> stringResource(
                                R.string.plan_detail_repeat_capsule,
                                repeatLabel ?: "",
                            )
                            else -> repeatLabel.orEmpty()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (task.isLocked) {
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.10f), RoundedCornerShape(WpRadius.sm))
                        .padding(horizontal = WpSpacing.sm, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.common_pinned),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                    )
                }
                Spacer(Modifier.width(WpSpacing.xs))
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.plan_detail_task_cd_edit))
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
}

@Composable
private fun EmptyTaskState() {
    Surface(
        shape = RoundedCornerShape(WpRadius.xxl),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().clickable { /* FAB 负责添加 */ },
    ) {
        Text(
            text = stringResource(R.string.plan_detail_no_tasks),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(WpSpacing.xxl),
        )
    }
}

private fun formatTime(minutes: Int): String {
    val total = ((minutes % 1440) + 1440) % 1440
    val h = total / 60
    val m = total % 60
    val dayShift = if (minutes >= 1440) " (+1)" else ""
    return "%02d:%02d%s".format(h, m, dayShift)
}

@Composable
private fun repeatRuleLabel(task: Task): String? = when (task.repeatRule) {
    RepeatRule.ONCE -> stringResource(R.string.repeat_once)
    RepeatRule.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    RepeatRule.DAILY -> stringResource(R.string.repeat_daily)
    RepeatRule.CUSTOM -> if (task.customDays.isNotEmpty()) {
        stringResource(R.string.repeat_custom)
    } else null
}
