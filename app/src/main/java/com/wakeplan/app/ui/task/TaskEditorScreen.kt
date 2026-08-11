package com.wakeplan.app.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.wakeplan.app.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val plannedDuration by viewModel.plannedDurationMinutes.collectAsStateWithLifecycle()
    val autoExtend by viewModel.autoExtend.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val segmentDur by viewModel.segmentDurationMinutes.collectAsStateWithLifecycle()
    val overload by viewModel.overloadMinutes.collectAsStateWithLifecycle()
    val isValid by viewModel.isValid.collectAsStateWithLifecycle()

    val isEdit = viewModel.taskId != null
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { onSaved() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) stringResource(R.string.task_edit_title) else stringResource(R.string.task_new_title)) },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
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

            // 计划时长
            OutlinedTextField(
                value = plannedDuration.toString(),
                onValueChange = { input ->
                    val v = input.toIntOrNull() ?: 0
                    viewModel.setPlannedDurationMinutes(v)
                },
                label = { Text(stringResource(R.string.task_duration_label_runtime)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // 段超载提示
            if (overload > 0) {
                OverloadWarning(overload = overload, segmentMinutes = segmentDur)
            }

            // autoExtend 开关
            ToggleRow(
                label = stringResource(R.string.task_auto_extend),
                description = stringResource(R.string.task_auto_extend_help),
                checked = autoExtend,
                onCheckedChange = viewModel::setAutoExtend,
            )

            // isLocked 开关
            ToggleRow(
                label = stringResource(R.string.task_pinned_label),
                description = stringResource(R.string.task_pinned_sub),
                checked = isLocked,
                onCheckedChange = viewModel::setIsLocked,
            )

            // 备注
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
                ) { Text(stringResource(R.string.plan_seg_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.plan_seg_delete_cancel)) }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
private fun OverloadWarning(overload: Int, segmentMinutes: Int) {
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
                    text = stringResource(R.string.task_overload_warning_runtime),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.task_overflow_warning_runtime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
