package com.wakeplan.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import java.time.format.DateTimeFormatter
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.MainActivity
import com.wakeplan.app.R
import com.wakeplan.app.data.prefs.LocaleHelper
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onTemplatesClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val leadMinutes by viewModel.leadMinutes.collectAsStateWithLifecycle(initialValue = UserPreferences.DEFAULT_LEAD_MINUTES)
    val startingEnabled by viewModel.taskStartingEnabled.collectAsStateWithLifecycle(initialValue = true)
    val overdueEnabled by viewModel.taskOverdueEnabled.collectAsStateWithLifecycle(initialValue = true)
    val completedEnabled by viewModel.taskCompletedEnabled.collectAsStateWithLifecycle(initialValue = false)
    val recapEnabled by viewModel.planRecapEnabled.collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ===== Phase 4.4 备份 state =====
    val exportState by backupViewModel.exportState.collectAsStateWithLifecycle()
    val importState by backupViewModel.importState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExportPlainDialog by remember { mutableStateOf(false) }
    var showExportEncryptedDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportEncrypted by remember { mutableStateOf(false) }
    var pendingExportPassphrase by remember { mutableStateOf<CharArray?>(null) }

    // ===== Phase 6.1 关于 state =====
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    // ===== Phase 6.3 语言 state =====
    var showLanguageDialog by remember { mutableStateOf(false) }
    val currentLocale = viewModel.currentLocale

    // SAF 导出 launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            backupViewModel.export(uri, passphrase = pendingExportPassphrase)
            pendingExportPassphrase = null
        }
    }

    // SAF 导入 launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            // 先尝试明文 import；如果是加密文件 importer 会抛 IllegalStateException 提示需要 passphrase
            backupViewModel.import(uri, passphrase = null)
        }
    }

    // Snackbar 反馈 export / import 结果
    val exportSuccessMsg = stringResource(R.string.settings_export_success_runtime_template)
    val exportFailedMsg = stringResource(R.string.settings_export_failed_runtime_template)
    val importSuccessMsg = stringResource(R.string.settings_import_success_runtime_template)
    val importFailedMsg = stringResource(R.string.settings_import_failed_runtime_template)
    val testNotificationTitle = stringResource(R.string.settings_test_notification_title)
    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is BackupViewModel.ExportState.Success -> {
                snackbarHostState.showSnackbar(exportSuccessMsg.format(s.bytesWritten / 1024))
                backupViewModel.consumeExportSuccess()
            }
            is BackupViewModel.ExportState.Error -> {
                snackbarHostState.showSnackbar(exportFailedMsg.format(s.message))
                backupViewModel.consumeExportError()
            }
            else -> Unit
        }
    }
    LaunchedEffect(importState) {
        when (val s = importState) {
            is BackupViewModel.ImportState.Success -> {
                val r = s.result
                snackbarHostState.showSnackbar(
                    importSuccessMsg.format(r.plansImported, r.tasksImported, r.completionsImported),
                )
                backupViewModel.consumeImportSuccess()
            }
            is BackupViewModel.ImportState.Error -> {
                snackbarHostState.showSnackbar(importFailedMsg.format(s.message))
                backupViewModel.consumeImportError()
            }
            else -> Unit
        }
    }

    // 监听 import 失败 — 如果是"需要 passphrase"则弹加密输入 dialog
    LaunchedEffect(importState) {
        val s = importState
        if (s is BackupViewModel.ImportState.Error && s.message.contains("requires passphrase")) {
            val uri = pendingImportUri
            if (uri != null) {
                pendingImportEncrypted = true
                showImportConfirmDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 模板库入口（Phase 1.4 保留）
            SettingsItem(
                title = stringResource(R.string.tpl_list_title),
                subtitle = stringResource(R.string.settings_templates_subtitle),
                onClick = onTemplatesClick,
            )

            // ===== Phase 6.3 语言 section（v0.3 P1 US：per-app locale）=====
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.language_section))

            SettingsItem(
                title = stringResource(R.string.language_title),
                subtitle = when (currentLocale) {
                    LocaleHelper.LocaleChoice.SYSTEM -> stringResource(R.string.language_summary_system)
                    LocaleHelper.LocaleChoice.ZH -> stringResource(R.string.language_summary_zh)
                    LocaleHelper.LocaleChoice.EN -> stringResource(R.string.language_summary_en)
                },
                onClick = { showLanguageDialog = true },
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.settings_section_notifications))

            // lead minutes 选择
            LeadMinutesRow(
                currentValue = leadMinutes,
                onChange = { viewModel.setLeadMinutes(it) },
            )

            // 4 渠道开关
            ToggleRow(
                title = stringResource(R.string.settings_notifications_starting),
                subtitle = stringResource(R.string.settings_notifications_starting_sub),
                checked = startingEnabled,
                onChange = { viewModel.setStartingEnabled(it) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_notifications_overdue),
                subtitle = stringResource(R.string.settings_notifications_overdue_sub),
                checked = overdueEnabled,
                onChange = { viewModel.setOverdueEnabled(it) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_notifications_completed),
                subtitle = stringResource(R.string.settings_notifications_completed_sub),
                checked = completedEnabled,
                onChange = { viewModel.setCompletedEnabled(it) },
            )
            ToggleRow(
                title = stringResource(R.string.settings_notifications_recap),
                subtitle = stringResource(R.string.settings_notifications_recap_sub),
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
                            taskTitle = testNotificationTitle,
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
                Text(stringResource(R.string.settings_test_notification))
            }

            // ===== Phase 4.4 备份 =====
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.settings_section_backup))

            Text(
                stringResource(R.string.settings_backup_intro_runtime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showExportPlainDialog = true },
                    enabled = exportState !is BackupViewModel.ExportState.Running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_backup_export_plain))
                }
                OutlinedButton(
                    onClick = { showExportEncryptedDialog = true },
                    enabled = exportState !is BackupViewModel.ExportState.Running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_backup_export_encrypted))
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { showImportConfirmDialog = true },
                enabled = importState !is BackupViewModel.ImportState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_backup_import))
            }

            // 进度指示
            if (exportState is BackupViewModel.ExportState.Running) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_backup_exporting), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (importState is BackupViewModel.ImportState.Running) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_backup_importing), style = MaterialTheme.typography.bodySmall)
                }
            }

            // ===== Phase 6.1 关于 section（v0.3 P1 US：隐私政策 + 版本 + 源代码）=====
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.about_section))

            SettingsItem(
                title = stringResource(R.string.about_privacy_policy_title),
                subtitle = stringResource(R.string.about_privacy_policy_summary),
                onClick = { showPrivacyPolicy = true },
            )
            SettingsItem(
                title = stringResource(R.string.about_version_title),
                subtitle = stringResource(
                    R.string.about_version_format,
                    viewModel.versionName,
                    viewModel.versionCode,
                ),
                onClick = {},
            )
            SettingsItem(
                title = stringResource(R.string.about_source_code_title),
                subtitle = stringResource(R.string.about_source_code_summary),
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                            context.getString(R.string.about_source_code_url),
                        ),
                    )
                    runCatching { context.startActivity(intent) }
                },
            )
        }

        // ===== 对话框（Phase 4.4）=====

        // 1. 导出明文确认
        if (showExportPlainDialog) {
            AlertDialog(
                onDismissRequest = { showExportPlainDialog = false },
                title = { Text(stringResource(R.string.settings_export_plain_dialog_title)) },
                text = {
                    Text(
                        stringResource(R.string.settings_export_plain_dialog_text_runtime),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExportPlainDialog = false
                        pendingExportPassphrase = null
                        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                        exportLauncher.launch("wakeplan-backup-$ts.json")
                    }) {
                        Text(stringResource(R.string.settings_pick_location))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportPlainDialog = false }) {
                        Text(stringResource(R.string.plan_seg_delete_cancel))
                    }
                },
            )
        }

        // 2. 导出加密：先弹密码输入，完了再启 SAF
        if (showExportEncryptedDialog) {
            PassphraseDialog(
                title = stringResource(R.string.settings_export_encrypted_dialog_title),
                message = stringResource(R.string.settings_export_encrypted_dialog_message_runtime),
                onConfirm = { pass ->
                    showExportEncryptedDialog = false
                    pendingExportPassphrase = pass
                    val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    exportLauncher.launch("wakeplan-backup-$ts-encrypted.json")
                },
                onDismiss = { showExportEncryptedDialog = false },
            )
        }

        // 3. 导入：先弹强提示
        if (showImportConfirmDialog && pendingImportUri == null) {
            ImportConfirmDialog(
                onConfirm = {
                    showImportConfirmDialog = false
                    pendingImportEncrypted = false
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                },
                onDismiss = { showImportConfirmDialog = false },
            )
        }

        // 6. 语言切换（Phase 6.3 v0.3 P1 US：per-app locale）
        if (showLanguageDialog) {
            LanguageChoiceDialog(
                current = currentLocale,
                onSelect = { choice ->
                    showLanguageDialog = false
                    if (choice != viewModel.currentLocale) {
                        viewModel.setLocale(choice)
                        // AppCompatDelegate.setApplicationLocales 已 set；recreate Activity 立即生效
                        (context as? MainActivity)?.recreate()
                    }
                },
                onDismiss = { showLanguageDialog = false },
            )
        }
    }
}

/**
 * 语言选择 dialog（v0.3 P1 US：per-app locale）
 * 三个选项：跟随系统 / 中文（简体）/ English
 * 当前选中的选项前面用 RadioButton 标记
 */
@Composable
private fun LanguageChoiceDialog(
    current: LocaleHelper.LocaleChoice,
    onSelect: (LocaleHelper.LocaleChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        LocaleHelper.LocaleChoice.SYSTEM to R.string.language_option_system,
        LocaleHelper.LocaleChoice.ZH to R.string.language_option_zh,
        LocaleHelper.LocaleChoice.EN to R.string.language_option_en,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Column {
                options.forEach { (choice, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = current == choice,
                            onClick = { onSelect(choice) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_privacy_policy_close))
            }
        },
    )
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
                    text = stringResource(R.string.settings_lead_time_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_lead_time_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = true }) {
                Text(if (currentValue == 0) stringResource(R.string.settings_lead_time_zero)
                     else stringResource(R.string.settings_lead_time_n_minutes, currentValue))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(if (v == 0) stringResource(R.string.settings_lead_time_zero)
                                     else stringResource(R.string.settings_lead_time_n_minutes, v)) },
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


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val appVersionProvider: com.wakeplan.app.data.backup.AppVersionProvider,
    private val localeHelper: LocaleHelper,
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

    
    val versionName: String get() = appVersionProvider.versionName
    val versionCode: Int get() = appVersionProvider.versionCode

    
    val currentLocale: LocaleHelper.LocaleChoice get() = localeHelper.current

    
    fun setLocale(choice: LocaleHelper.LocaleChoice) {
        localeHelper.apply(choice)
    }

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
