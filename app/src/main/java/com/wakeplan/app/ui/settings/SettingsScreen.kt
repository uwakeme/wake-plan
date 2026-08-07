package com.wakeplan.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.notification.NotificationBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 设置屏 — Phase 3.3 加通知配置
 * 包含 lead minutes 选择 + 4 渠道开关 + 测试通知按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onTemplatesClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val leadMinutes by viewModel.leadMinutes.collectAsStateWithLifecycle(initialValue = UserPreferences.DEFAULT_LEAD_MINUTES)
    val startingEnabled by viewModel.taskStartingEnabled.collectAsStateWithLifecycle(initialValue = true)
    val overdueEnabled by viewModel.taskOverdueEnabled.collectAsStateWithLifecycle(initialValue = true)
    val completedEnabled by viewModel.taskCompletedEnabled.collectAsStateWithLifecycle(initialValue = false)
    val recapEnabled by viewModel.planRecapEnabled.collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 模板库入口（Phase 1.4 保留）
            SettingsItem(
                title = "模板库",
                subtitle = "3 个内置模板（标准晨练 / 标准学习 / 周末阅读）",
                onClick = onTemplatesClick,
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader("通知")

            // lead minutes 选择
            LeadMinutesRow(
                currentValue = leadMinutes,
                onChange = { viewModel.setLeadMinutes(it) },
            )

            // 4 渠道开关
            ToggleRow(
                title = "任务开始提醒",
                subtitle = "任务开始前 N 分钟推送",
                checked = startingEnabled,
                onChange = { viewModel.setStartingEnabled(it) },
            )
            ToggleRow(
                title = "任务超时提醒",
                subtitle = "任务超时未完成时循环提醒",
                checked = overdueEnabled,
                onChange = { viewModel.setOverdueEnabled(it) },
            )
            ToggleRow(
                title = "任务完成确认",
                subtitle = "v0.1 占位，v0.2 启用",
                checked = completedEnabled,
                onChange = { viewModel.setCompletedEnabled(it) },
            )
            ToggleRow(
                title = "每日计划回顾",
                subtitle = "每日 21:00 推送（v0.1 占位）",
                checked = recapEnabled,
                onChange = { viewModel.setRecapEnabled(it) },
            )

            // 测试通知按钮
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val testCompletion = TaskCompletion(
                            id = "test-${System.currentTimeMillis()}",
                            taskId = "test-task",
                            planId = "test-plan",
                            scheduledDate = java.time.LocalDate.now(),
                            plannedStart = LocalDateTime.now(),
                            plannedEnd = LocalDateTime.now().plusMinutes(30),
                            status = TaskStatus.PENDING,
                        )
                        val notification = NotificationBuilder.buildStarting(
                            context = context,
                            completion = testCompletion,
                            taskTitle = "测试通知",
                            leadMinutes = leadMinutes,
                        )
                        NotificationManagerCompat.from(context).notify(
                            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                            notification,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("发测试通知")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun LeadMinutesRow(
    currentValue: Int,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = UserPreferences.ALLOWED_LEAD_VALUES.toList().sorted()
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "提前提醒",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "任务开始前多少分钟推送通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = true }) {
                Text(if (currentValue == 0) "不提前" else "$currentValue 分钟")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(if (v == 0) "不提前" else "$v 分钟") },
                        onClick = {
                            onChange(v)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Settings ViewModel — Phase 3.3
 * 包装 UserPreferences，把 lead minutes + 4 toggle 暴露成 StateFlow
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
) : ViewModel() {

    val leadMinutes = preferences.taskStartingLeadMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences.DEFAULT_LEAD_MINUTES)

    val taskStartingEnabled = preferences.taskStartingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val taskOverdueEnabled = preferences.taskOverdueEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val taskCompletedEnabled = preferences.taskCompletedEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val planRecapEnabled = preferences.planRecapEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLeadMinutes(minutes: Int) {
        viewModelScope.launch { preferences.setTaskStartingLeadMinutes(minutes) }
    }
    fun setStartingEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTaskStartingEnabled(enabled) }
    }
    fun setOverdueEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTaskOverdueEnabled(enabled) }
    }
    fun setCompletedEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTaskCompletedEnabled(enabled) }
    }
    fun setRecapEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setPlanRecapEnabled(enabled) }
    }
}
