package com.wakeplan.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.wakeplan.app.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SegmentEditorViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val startMinutes by viewModel.startMinutes.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val endMinutes by viewModel.endMinutes.collectAsStateWithLifecycle()
    val repeatRule by viewModel.repeatRule.collectAsStateWithLifecycle()
    val customDays by viewModel.customDays.collectAsStateWithLifecycle()
    val overlapping by viewModel.overlappingSegments.collectAsStateWithLifecycle()
    val isValid by viewModel.isValid.collectAsStateWithLifecycle()

    val isEdit = viewModel.segmentId != null

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { onSaved() }
    }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) stringResource(R.string.plan_seg_title_edit) else stringResource(R.string.plan_seg_title_new)) },
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
                                contentDescription = stringResource(R.string.plan_seg_delete),
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.plan_seg_label_optional)) },
                placeholder = { Text(stringResource(R.string.plan_seg_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 时间段选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.plan_seg_start_runtime, formatTime(startMinutes)))
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.plan_seg_end_runtime, formatTime(endMinutes)))
                }
            }

            // 段重叠检测（PRD AC-02.3：内联提示）
            if (overlapping.isNotEmpty()) {
                OverlapWarning(overlapping)
            }

            // RepeatRule 选择
            Text(
                text = stringResource(R.string.plan_seg_repeat),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RepeatRule.entries.forEachIndexed { index, rule ->
                    SegmentedButton(
                        selected = rule == repeatRule,
                        onClick = { viewModel.setRepeatRule(rule) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = RepeatRule.entries.size,
                        ),
                    ) {
                        Text(repeatRuleLabel(context, rule), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (repeatRule == RepeatRule.CUSTOM) {
                Text(
                    text = stringResource(R.string.plan_seg_select_weekday),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DayOfWeek.entries.sortedBy { it.value }.forEach { day ->
                        FilterChip(
                            selected = day in customDays,
                            onClick = { viewModel.toggleCustomDay(day) },
                            label = { Text(dayLabel(day), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (customDays.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plan_seg_custom_help_v2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

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

    // TimePicker dialogs
    if (showStartPicker) {
        TimePickerDialog(
            initialMinutes = startMinutes,
            onConfirm = { h, m ->
                viewModel.setStartMinutes(h * 60 + m)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinutes = endMinutes,
            onConfirm = { h, m ->
                viewModel.setEndMinutes(h * 60 + m)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.plan_seg_delete_title)) },
            text = { Text(stringResource(R.string.plan_seg_delete_msg_v2)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    }
                ) { Text(stringResource(R.string.plan_seg_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.plan_seg_delete_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(initialMinutes) {
        ((initialMinutes % 1440) + 1440) % 1440
    }
    val state = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.backup_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.plan_seg_delete_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun OverlapWarning(overlapping: List<com.wakeplan.app.domain.model.TimeSegment>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.plan_seg_overlap_count_runtime, overlapping.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = overlapping.joinToString { "${formatTime(it.startMinutes)}-${formatTime(it.endMinutes)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private fun repeatRuleLabel(context: android.content.Context, rule: RepeatRule): String = when (rule) {
    RepeatRule.ONCE -> context.getString(R.string.plan_seg_repeat_once)
    RepeatRule.WEEKDAYS -> context.getString(R.string.plan_seg_repeat_weekdays)
    RepeatRule.DAILY -> context.getString(R.string.plan_seg_repeat_daily)
    RepeatRule.CUSTOM -> context.getString(R.string.plan_seg_repeat_custom)
}

private fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)

private fun formatTime(minutes: Int): String {
    val total = ((minutes % 1440) + 1440) % 1440
    val h = total / 60
    val m = total % 60
    val dayShift = if (minutes >= 1440) "+1" else ""
    return "%02d:%02d%s".format(h, m, if (dayShift.isNotEmpty()) " $dayShift" else "")
}
