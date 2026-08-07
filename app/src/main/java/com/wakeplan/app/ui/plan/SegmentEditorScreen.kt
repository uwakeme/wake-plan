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

/**
 * 段编辑器屏
 * - 支持 create / edit 模式
 * - 段重叠检测内联提示（PRD AC-02.3）
 * - TimePicker dialog（Compose 1.6+ 没有 TimePickerDialog，手包）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SegmentEditorViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val startMinutes by viewModel.startMinutes.collectAsStateWithLifecycle()
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
                title = { Text(if (isEdit) "编辑段" else "新建段") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除段",
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
                label = { Text("段标题（可选）") },
                placeholder = { Text("例如：晨练 / 学习 / 副业") },
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
                    Text("开始 ${formatTime(startMinutes)}")
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("结束 ${formatTime(endMinutes)}")
                }
            }

            // 段重叠检测（PRD AC-02.3：内联提示）
            if (overlapping.isNotEmpty()) {
                OverlapWarning(overlapping)
            }

            // RepeatRule 选择
            Text(
                text = "重复",
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
                        Text(repeatRuleLabel(rule), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (repeatRule == RepeatRule.CUSTOM) {
                Text(
                    text = "选择星期",
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
                        text = "CUSTOM 规则必须至少选 1 天",
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
                Text("保存")
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
            title = { Text("删除段？") },
            text = { Text("段内任务也会一起删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
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
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
                    text = "与 ${overlapping.size} 个段重叠",
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

private fun repeatRuleLabel(rule: RepeatRule): String = when (rule) {
    RepeatRule.ONCE -> "仅一次"
    RepeatRule.WEEKDAYS -> "工作日"
    RepeatRule.DAILY -> "每天"
    RepeatRule.CUSTOM -> "自定义"
}

private fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)

private fun formatTime(minutes: Int): String {
    val total = ((minutes % 1440) + 1440) % 1440
    val h = total / 60
    val m = total % 60
    val dayShift = if (minutes >= 1440) "+1" else ""
    return "%02d:%02d%s".format(h, m, if (dayShift.isNotEmpty()) " $dayShift" else "")
}
