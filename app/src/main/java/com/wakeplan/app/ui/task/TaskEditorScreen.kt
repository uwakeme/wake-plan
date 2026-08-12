package com.wakeplan.app.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * 任务编辑器 — v0.3 扁平化
 *
 * 字段（v0.3）：
 * - 标题（必填，<= 30 字）
 * - 开始时间 / 结束时间（TimePicker，跨午夜 endMinutes > 1440 自动识别）
 * - 重复规则（ONCE / WEEKDAYS / DAILY / CUSTOM）
 * - 自定义星期（CUSTOM 时展开）
 * - 锁定（Switch，钉死，链式延期到此停止）
 * - 备注（可选）
 * - 删除（编辑模式）
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val startMinutes by viewModel.startMinutes.collectAsStateWithLifecycle()
    val endMinutes by viewModel.endMinutes.collectAsStateWithLifecycle()
    val repeatRule by viewModel.repeatRule.collectAsStateWithLifecycle()
    val customDays by viewModel.customDays.collectAsStateWithLifecycle()
    val autoExtend by viewModel.autoExtend.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val isValid by viewModel.isValid.collectAsStateWithLifecycle()

    val isEdit = viewModel.taskId != null
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { onSaved() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) stringResource(R.string.task_edit_title)
                        else stringResource(R.string.task_new_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.task_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 标题
            OutlinedTextField(
                value = title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.task_title_label)) },
                placeholder = { Text(stringResource(R.string.task_title_hint)) },
                singleLine = true,
                isError = title.isNotEmpty() && title.length > TaskEditorViewModel.MAX_TITLE_LENGTH,
                supportingText = {
                    Text("${title.length} / ${TaskEditorViewModel.MAX_TITLE_LENGTH}")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // 2. 起止时间
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.task_start_time_label) + " " +
                        stringResource(R.string.task_time_to) + " " +
                        stringResource(R.string.task_end_time_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TimeField(
                        modifier = Modifier.weight(1f),
                        minutes = startMinutes,
                        onClick = { pickerTarget = PickerTarget.START },
                    )
                    TimeField(
                        modifier = Modifier.weight(1f),
                        minutes = endMinutes,
                        onClick = { pickerTarget = PickerTarget.END },
                    )
                }
                if (endMinutes > 24 * 60) {
                    val durationMin = endMinutes - startMinutes
                    Text(
                        text = stringResource(R.string.task_cross_midnight_hint, durationMin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (endMinutes <= startMinutes) {
                    Text(
                        text = stringResource(R.string.task_end_before_start_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // 3. 重复规则
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.task_repeat_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RepeatRule.values().forEach { rule ->
                        FilterChip(
                            selected = repeatRule == rule,
                            onClick = { viewModel.setRepeatRule(rule) },
                            label = { Text(ruleLabel(rule)) },
                        )
                    }
                }
                if (repeatRule == RepeatRule.CUSTOM) {
                    Text(
                        text = stringResource(R.string.task_custom_days_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DayOfWeek.values().forEach { day ->
                            FilterChip(
                                selected = day in customDays,
                                onClick = { viewModel.toggleCustomDay(day) },
                                label = { Text(dayLabel(day)) },
                            )
                        }
                    }
                }
            }

            // 4. 自动延期（UI 正面表达；底层对应 isLocked = !autoExtend）
            ToggleRow(
                label = stringResource(R.string.task_auto_extend_label),
                description = stringResource(R.string.task_auto_extend_sub),
                leadingIcon = if (autoExtend) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                checked = autoExtend,
                onCheckedChange = viewModel::setAutoExtend,
            )

            // 5. 备注
            OutlinedTextField(
                value = note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.task_notes_label)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.task_save))
            }
        }
    }

    pickerTarget?.let { target ->
        TimePickerDialog(
            title = stringResource(
                if (target == PickerTarget.START) R.string.task_picker_start_title
                else R.string.task_picker_end_title,
            ),
            initialMinutes = if (target == PickerTarget.START) startMinutes else endMinutes,
            onConfirm = { picked ->
                if (target == PickerTarget.START) viewModel.setStartMinutes(picked)
                else viewModel.setEndMinutes(picked)
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.task_delete_title)) },
            text = { Text(stringResource(R.string.task_delete_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    }
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private enum class PickerTarget { START, END }

@Composable
private fun TimeField(
    modifier: Modifier = Modifier,
    minutes: Int,
    onClick: () -> Unit,
) {
    OutlinedTextField(
        value = formatHHmm(minutes),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        modifier = modifier,
        trailingIcon = {
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.common_confirm))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60) % 24,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        // Map hour 0..23 to absolute minutes: support cross-midnight by computing
                        // delta vs initial and clamping. We use a simple convention:
                        // if picked hour < initial hour (caller picked earlier), assume +24h
                        // cross-midnight when current state is ambiguous.
                        val absoluteMinutes = state.hour * 60 + state.minute
                        val initialHour = (initialMinutes / 60) % 24
                        val candidate = if (state.hour < initialHour) (state.hour + 24) * 60 + state.minute
                                        else absoluteMinutes
                        onConfirm(candidate)
                    }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ruleLabel(rule: RepeatRule): String = stringResource(
    when (rule) {
        RepeatRule.ONCE -> R.string.repeat_once
        RepeatRule.WEEKDAYS -> R.string.repeat_weekdays
        RepeatRule.DAILY -> R.string.repeat_daily
        RepeatRule.CUSTOM -> R.string.repeat_custom
    },
)

@Composable
private fun dayLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.weekday_mon
        DayOfWeek.TUESDAY -> R.string.weekday_tue
        DayOfWeek.WEDNESDAY -> R.string.weekday_wed
        DayOfWeek.THURSDAY -> R.string.weekday_thu
        DayOfWeek.FRIDAY -> R.string.weekday_fri
        DayOfWeek.SATURDAY -> R.string.weekday_sat
        DayOfWeek.SUNDAY -> R.string.weekday_sun
    },
)

/** Format absolute minutes (0..2880) as "HH:mm". 25*60 → "25:00", 26*60+30 → "26:30". */
private fun formatHHmm(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val h = safe / 60
    val m = safe % 60
    return "%d:%02d".format(h, m)
}
