package com.wakeplan.app.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.ui.common.TimeFormat
import com.wakeplan.app.ui.common.WpRadius
import com.wakeplan.app.ui.common.WpTimePickerDialog
import java.time.LocalTime

/**
 * 快速录入任务底部弹层 — v0.4（INTERNAL_TEST 已知问题「建计划要 3-4 步」的最短路径）
 *
 * 计划 chips（默认选最近更新的活跃计划）+ 标题 + 起止时间，保存经
 * TodayViewModel.quickAddTask 建任务并立即铺当天实例。不动 schema、不进复杂编辑器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddTaskSheet(
    plans: List<Plan>,
    defaultPlanId: String?,
    onSave: (planId: String, title: String, startMinutes: Int, endMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var planId by remember { mutableStateOf(defaultPlanId ?: plans.firstOrNull()?.id) }
    var title by remember { mutableStateOf("") }
    var startMinutes by remember { mutableStateOf(9 * 60) }
    var endMinutes by remember { mutableStateOf(9 * 60 + 30) }
    var picking by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.today_quick_add_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (plans.isEmpty()) {
                Text(
                    text = stringResource(R.string.today_quick_add_no_plan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.today_quick_add_plan_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    plans.forEach { plan ->
                        FilterChip(
                            selected = planId == plan.id,
                            onClick = { planId = plan.id },
                            label = { Text(plan.title) },
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title_label)) },
                    placeholder = { Text(stringResource(R.string.task_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TimeField(
                        label = stringResource(R.string.task_start_time_label),
                        minutes = startMinutes,
                        onClick = { picking = "start" },
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label = stringResource(R.string.task_end_time_label),
                        minutes = endMinutes,
                        onClick = { picking = "end" },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (endMinutes <= startMinutes) {
                    Text(
                        text = stringResource(R.string.task_end_before_start_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = {
                        val pid = planId
                        if (pid != null && title.isNotBlank() && endMinutes > startMinutes) {
                            onSave(pid, title.trim(), startMinutes, endMinutes)
                            onDismiss()
                        }
                    },
                    enabled = planId != null && title.isNotBlank() && endMinutes > startMinutes,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(WpRadius.md),
                ) {
                    Text(stringResource(R.string.today_quick_add_save))
                }
            }
        }
    }

    if (picking == "start") {
        WpTimePickerDialog(
            title = stringResource(R.string.task_start_time_label),
            initialMinutes = startMinutes,
            stateKey = "start",
            onConfirm = {
                startMinutes = it
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    if (picking == "end") {
        WpTimePickerDialog(
            title = stringResource(R.string.task_end_time_label),
            initialMinutes = endMinutes,
            stateKey = "end",
            onConfirm = {
                endMinutes = it
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(WpRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = TimeFormat.minutesOfDay(minutes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
