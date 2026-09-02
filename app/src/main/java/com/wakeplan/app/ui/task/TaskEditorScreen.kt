package com.wakeplan.app.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.ui.common.WpRadius
import java.time.DayOfWeek

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    var showNoteEditor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { onSaved() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(
            isEdit = isEdit,
            isValid = isValid,
            onBack = onBack,
            onSave = viewModel::save,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            TitleInputCard(
                value = title,
                onValueChange = viewModel::setTitle,
            )

            ClickableRowCard(
                icon = Icons.Outlined.AccessTime,
                label = stringResource(R.string.task_start_time_label) + " " +
                    stringResource(R.string.task_time_to) + " " +
                    stringResource(R.string.task_end_time_label),
                value = formatTimeRange(startMinutes, endMinutes),
                onClick = { pickerTarget = PickerTarget.START },
            )

            if (endMinutes > 24 * 60) {
                val durationMin = endMinutes - startMinutes
                Text(
                    text = stringResource(R.string.task_cross_midnight_hint, durationMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else if (endMinutes <= startMinutes) {
                Text(
                    text = stringResource(R.string.task_end_before_start_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            SectionTitle(stringResource(R.string.task_repeat_label))
            RepeatChips(
                selected = repeatRule,
                onSelect = viewModel::setRepeatRule,
            )

            if (repeatRule == RepeatRule.CUSTOM) {
                DayPickerRow(
                    selectedDays = customDays,
                    onToggle = viewModel::toggleCustomDay,
                )
            }

            ToggleRowCard(
                icon = if (autoExtend) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                label = stringResource(R.string.task_auto_extend_label),
                description = stringResource(R.string.task_auto_extend_sub),
                checked = autoExtend,
                onCheckedChange = viewModel::setAutoExtend,
            )

            ClickableRowCard(
                icon = Icons.Outlined.EditNote,
                label = stringResource(R.string.task_notes_label),
                value = if (note.isBlank()) stringResource(R.string.task_notes_label) else note,
                isPlaceholder = note.isBlank(),
                onClick = { showNoteEditor = true },
                showChevron = false,
            )

            if (isEdit) {
                Spacer(Modifier.height(8.dp))
                DeleteButton(onClick = { showDeleteDialog = true })
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    pickerTarget?.let { target ->
        TimePickerDialog(
            title = stringResource(
                if (target == PickerTarget.START) R.string.task_picker_start_title
                else R.string.task_picker_end_title,
            ),
            initialMinutes = if (target == PickerTarget.START) startMinutes else endMinutes,
            isStartPicker = target == PickerTarget.START,
            onConfirm = { picked ->
                if (target == PickerTarget.START) {
                    viewModel.setStartMinutes(picked)
                    pickerTarget = PickerTarget.END
                } else {
                    viewModel.setEndMinutes(picked)
                    pickerTarget = null
                }
            },
            onDismiss = { pickerTarget = null },
        )
    }

    if (showNoteEditor) {
        NoteEditorDialog(
            value = note,
            onValueChange = viewModel::setNote,
            onDismiss = { showNoteEditor = false },
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
private fun TopBar(
    isEdit: Boolean,
    isValid: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 44.dp)
            .height(56.dp)
            .padding(horizontal = 20.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Text(
                text = stringResource(R.string.common_cancel),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
        }

        Text(
            text = if (isEdit) stringResource(R.string.task_edit_title)
            else stringResource(R.string.task_new_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center),
        )

        TextButton(
            onClick = onSave,
            enabled = isValid,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Text(
                text = stringResource(R.string.common_save),
                fontSize = 15.sp,
                color = if (isValid) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TitleInputCard(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.task_title_hint),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = {
                    if (it.length <= TaskEditorViewModel.MAX_TITLE_LENGTH) {
                        onValueChange(it)
                    }
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun ClickableRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isPlaceholder: Boolean = false,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )

            Text(
                text = value,
                fontSize = 15.sp,
                color = if (isPlaceholder) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(end = if (showChevron) 4.dp else 0.dp),
            )

            if (showChevron) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RepeatChips(
    selected: RepeatRule,
    onSelect: (RepeatRule) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RepeatRule.values().forEach { rule ->
            val isSelected = selected == rule
            val bgColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { onSelect(rule) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = ruleLabel(rule),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun DayPickerRow(
    selectedDays: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val daysOrder = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        )
        daysOrder.forEach { day ->
            val isSelected = day in selectedDays
            val bgColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            }
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val borderModifier = if (!isSelected) {
                Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
            } else Modifier

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .weight(1f)
                    .then(borderModifier)
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayLabel(day),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.common_delete),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    isStartPicker: Boolean,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // key() 让每次重新进入 picker 时 state 强制重建，避免复用导致字段停留在分钟
    val state = key(initialMinutes, isStartPicker) {
        rememberTimePickerState(
            initialHour = (initialMinutes / 60) % 24,
            initialMinute = initialMinutes % 60,
            is24Hour = true,
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
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
                        // Picker 只支持当日 0-23 小时；结束时间不再自动跨日
                        // 跨午夜场景暂时禁用：上层 save() 会校验 endMinutes > startMinutes
                        // 如果用户选了比开始时间小的值，setEndMinutes 会保持原值，
                        // UI 校验阻止保存。
                        onConfirm(state.hour * 60 + state.minute)
                    }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.task_notes_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    minLines = 3,
                    maxLines = 5,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_confirm))
                    }
                }
            }
        }
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

private fun formatHHmm(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val h = safe / 60
    val m = safe % 60
    return "%d:%02d".format(h, m)
}

private fun formatTimeRange(startMinutes: Int, endMinutes: Int): String {
    val start = formatHHmm(startMinutes)
    val endH = (endMinutes / 60) % 24
    val endM = endMinutes % 60
    val end = "%d:%02d".format(endH, endM)
    return "$start - $end"
}
