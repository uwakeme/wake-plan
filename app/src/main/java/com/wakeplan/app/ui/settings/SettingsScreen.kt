@file:Suppress("DEPRECATION")

package com.wakeplan.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.MainActivity
import com.wakeplan.app.R
import com.wakeplan.app.data.prefs.LocaleHelper
import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.notification.NotificationBuilder
import com.wakeplan.app.notification.NotificationPermissionChecker
import com.wakeplan.app.ui.common.WpRadius
import com.wakeplan.app.ui.theme.PlanSideProjectDark
import com.wakeplan.app.ui.theme.PlanSideProjectLight
import com.wakeplan.app.ui.theme.WpBgOverlayDark
import com.wakeplan.app.ui.theme.WpBorderStrongDark
import com.wakeplan.app.ui.theme.WpErrorDark
import com.wakeplan.app.ui.theme.WpErrorLight
import com.wakeplan.app.ui.theme.WpInfoDark
import com.wakeplan.app.ui.theme.WpInfoLight
import com.wakeplan.app.ui.theme.WpPrimaryDark
import com.wakeplan.app.ui.theme.WpSuccessDark
import com.wakeplan.app.ui.theme.WpSuccessLight
import com.wakeplan.app.ui.theme.WpTextPrimaryDark
import com.wakeplan.app.ui.theme.WpTextSecondaryDark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private enum class SettingIconTint {
    AMBER, BLUE, GREEN, RED, PURPLE
}

@Composable
private fun SettingIcon(
    icon: ImageVector,
    tint: SettingIconTint,
    modifier: Modifier = Modifier,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val (bg, fg) = when (tint) {
        SettingIconTint.AMBER -> MaterialTheme.colorScheme.primary.let {
            it.copy(alpha = 0.12f) to it
        }
        SettingIconTint.BLUE -> {
            val blue = if (isDark) WpInfoDark else WpInfoLight
            blue.copy(alpha = 0.12f) to blue
        }
        SettingIconTint.GREEN -> {
            val green = if (isDark) WpSuccessDark else WpSuccessLight
            green.copy(alpha = 0.12f) to green
        }
        SettingIconTint.RED -> {
            val red = if (isDark) WpErrorDark else WpErrorLight
            red.copy(alpha = 0.12f) to red
        }
        SettingIconTint.PURPLE -> {
            val purple = if (isDark) PlanSideProjectDark else PlanSideProjectLight
            purple.copy(alpha = 0.12f) to purple
        }
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(WpRadius.md))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.72.sp,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    iconTint: SettingIconTint,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingIcon(icon = icon, tint = iconTint)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = WpTextPrimaryDark,
                    checkedTrackColor = WpPrimaryDark,
                    checkedBorderColor = WpPrimaryDark,
                    uncheckedThumbColor = WpTextSecondaryDark,
                    uncheckedTrackColor = Color(0x1A8492AD),
                    uncheckedBorderColor = WpBorderStrongDark,
                ),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun SettingNavRow(
    icon: ImageVector,
    iconTint: SettingIconTint,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingIcon(icon = icon, tint = iconTint)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun AboutAppCard(
    versionName: String,
    versionCode: Int,
) {
    Surface(
        shape = RoundedCornerShape(WpRadius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(WpRadius.xl))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "W",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Wake Plan",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "v$versionName (build $versionCode)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.about_tagline),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

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
    val retentionDays by viewModel.completionRetentionDays.collectAsStateWithLifecycle(initialValue = 0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportState by backupViewModel.exportState.collectAsStateWithLifecycle()
    val importState by backupViewModel.importState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExportPlainDialog by remember { mutableStateOf(false) }
    var showExportEncryptedDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showExportChooserDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportEncrypted by remember { mutableStateOf(false) }
    var pendingExportPassphrase by remember { mutableStateOf<CharArray?>(null) }

    var showPrivacyPolicy by remember { mutableStateOf(false) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    val currentLocale = viewModel.currentLocale

    var leadMenuExpanded by remember { mutableStateOf(false) }

    // v0.5：CSV 导出 launcher（复用 ExportState 状态机与结果消费）
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) backupViewModel.exportCsv(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            backupViewModel.export(uri, passphrase = pendingExportPassphrase)
            pendingExportPassphrase = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            backupViewModel.import(uri, passphrase = null)
        }
    }

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

    LaunchedEffect(importState) {
        val s = importState
        // 类型化状态触发密码框（v0.4.1 前靠 message.contains("requires passphrase")）
        if (s is BackupViewModel.ImportState.NeedsPassphrase) {
            val uri = pendingImportUri
            if (uri != null) {
                pendingImportEncrypted = true
                showImportConfirmDialog = true
                backupViewModel.consumeImportError()
            }
        }
    }

    val leadValueText = if (leadMinutes == 0) {
        stringResource(R.string.settings_lead_time_zero)
    } else {
        stringResource(R.string.settings_lead_time_n_minutes, leadMinutes)
    }
    val languageValueText = when (currentLocale) {
        LocaleHelper.LocaleChoice.SYSTEM -> stringResource(R.string.language_summary_system)
        LocaleHelper.LocaleChoice.ZH -> stringResource(R.string.language_summary_zh)
        LocaleHelper.LocaleChoice.EN -> stringResource(R.string.language_summary_en)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        // 布局自己处理系统 insets（44dp 状态栏 spacer），Scaffold 侧置零
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                top = 44.dp,
                bottom = 100.dp,
                start = 20.dp,
                end = 20.dp,
            ),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.04).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // 品牌 / 应用介绍卡片：置于设置页最顶部（版本号 + 项目理念）
            item {
                AboutAppCard(
                    versionName = viewModel.versionName,
                    versionCode = viewModel.versionCode,
                )
            }

            item { SectionTitle(stringResource(R.string.settings_section_templates)) }
            item {
                SettingsGroupCard {
                    SettingNavRow(
                        icon = Icons.Outlined.ViewList,
                        iconTint = SettingIconTint.BLUE,
                        title = stringResource(R.string.tpl_list_title),
                        subtitle = stringResource(R.string.settings_templates_subtitle),
                        onClick = onTemplatesClick,
                        showDivider = false,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.language_section)) }
            item {
                SettingsGroupCard {
                    SettingNavRow(
                        icon = Icons.Outlined.Language,
                        iconTint = SettingIconTint.BLUE,
                        title = stringResource(R.string.language_title),
                        value = languageValueText,
                        onClick = { showLanguageDialog = true },
                        showDivider = false,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.settings_section_notifications)) }
            item {
                SettingsGroupCard {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { leadMenuExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingIcon(icon = Icons.Outlined.Schedule, tint = SettingIconTint.AMBER)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_lead_time_title),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.settings_lead_time_sub),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Box {
                                TextButton(onClick = { leadMenuExpanded = true }) {
                                    Text(
                                        text = leadValueText,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                val options = UserPreferences.ALLOWED_LEAD_VALUES.toList().sorted()
                                DropdownMenu(
                                    expanded = leadMenuExpanded,
                                    onDismissRequest = { leadMenuExpanded = false },
                                ) {
                                    options.forEach { v ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (v == 0) stringResource(R.string.settings_lead_time_zero)
                                                    else stringResource(R.string.settings_lead_time_n_minutes, v),
                                                )
                                            },
                                            onClick = {
                                                viewModel.setLeadMinutes(v)
                                                leadMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SettingToggleRow(
                        icon = Icons.Outlined.NotificationsActive,
                        iconTint = SettingIconTint.AMBER,
                        title = stringResource(R.string.settings_notifications_starting),
                        subtitle = stringResource(R.string.settings_notifications_starting_sub),
                        checked = startingEnabled,
                        onChange = { viewModel.setStartingEnabled(it) },
                    )
                    SettingToggleRow(
                        icon = Icons.Outlined.ErrorOutline,
                        iconTint = SettingIconTint.RED,
                        title = stringResource(R.string.settings_notifications_overdue),
                        subtitle = stringResource(R.string.settings_notifications_overdue_sub),
                        checked = overdueEnabled,
                        onChange = { viewModel.setOverdueEnabled(it) },
                    )
                    SettingToggleRow(
                        icon = Icons.Outlined.CheckCircle,
                        iconTint = SettingIconTint.GREEN,
                        title = stringResource(R.string.settings_notifications_completed),
                        subtitle = stringResource(R.string.settings_notifications_completed_sub),
                        checked = completedEnabled,
                        onChange = { viewModel.setCompletedEnabled(it) },
                    )
                    // v0.5 实装：每日 21:00 当日完成率小结（RecapWorker）
                    SettingToggleRow(
                        icon = Icons.Outlined.Insights,
                        iconTint = SettingIconTint.PURPLE,
                        title = stringResource(R.string.settings_notifications_recap),
                        subtitle = stringResource(R.string.settings_notifications_recap_sub),
                        checked = recapEnabled,
                        onChange = { viewModel.setRecapEnabled(it) },
                        showDivider = false,
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
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
                            // Android 13+ 未授权 POST_NOTIFICATIONS 时直接跳过（lint MissingPermission）
                            if (!NotificationPermissionChecker.hasPostNotificationsPermission(context)) {
                                return@launch
                            }
                            NotificationManagerCompat.from(context).let { notifier ->
                                try {
                                    notifier.notify(
                                        (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                                        notification,
                                    )
                                } catch (_: SecurityException) {
                                    // 通知权限未授权，测试通知静默跳过
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(WpRadius.lg),
                ) {
                    Text(
                        text = stringResource(R.string.settings_test_notification),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.settings_section_backup)) }
            item {
                SettingsGroupCard {
                    SettingNavRow(
                        icon = Icons.Outlined.FileDownload,
                        iconTint = SettingIconTint.GREEN,
                        title = stringResource(R.string.settings_backup_export_title),
                        subtitle = stringResource(R.string.settings_backup_intro_runtime).replace("\\n", "\n"),
                        onClick = { showExportChooserDialog = true },
                    )
                    SettingNavRow(
                        icon = Icons.Outlined.FileUpload,
                        iconTint = SettingIconTint.PURPLE,
                        title = stringResource(R.string.settings_backup_import),
                        subtitle = stringResource(R.string.settings_import_confirm_text),
                        onClick = { showImportConfirmDialog = true },
                    )
                    // v0.5：任务执行记录 CSV 导出（SAF，同备份导出状态机）
                    SettingNavRow(
                        icon = Icons.Outlined.TableView,
                        iconTint = SettingIconTint.AMBER,
                        title = stringResource(R.string.settings_csv_export),
                        subtitle = stringResource(R.string.settings_csv_export_sub),
                        onClick = {
                            val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                            csvLauncher.launch("wakeplan-completions-$ts.csv")
                        },
                        showDivider = false,
                    )
                }
            }

            if (exportState is BackupViewModel.ExportState.Running || importState is BackupViewModel.ImportState.Running) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (exportState is BackupViewModel.ExportState.Running) {
                                stringResource(R.string.settings_backup_exporting)
                            } else {
                                stringResource(R.string.settings_backup_importing)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_retention_title)) }
            item {
                SettingsGroupCard {
                    SettingNavRow(
                        icon = Icons.Outlined.CleaningServices,
                        iconTint = SettingIconTint.GREEN,
                        title = stringResource(R.string.settings_retention_title),
                        subtitle = retentionLabel(retentionDays),
                        onClick = { showRetentionDialog = true },
                        showDivider = false,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.about_section)) }
            item {
                SettingsGroupCard {
                    SettingNavRow(
                        icon = Icons.Outlined.PrivacyTip,
                        iconTint = SettingIconTint.RED,
                        title = stringResource(R.string.about_privacy_policy_title),
                        subtitle = stringResource(R.string.about_privacy_policy_summary),
                        onClick = { showPrivacyPolicy = true },
                        showDivider = false,
                    )
                }
            }
        }

        if (showExportChooserDialog) {
            AlertDialog(
                onDismissRequest = { showExportChooserDialog = false },
                title = { Text(stringResource(R.string.settings_backup_export_plain)) },
                text = {
                    Text(
                        stringResource(R.string.settings_backup_intro_runtime).replace("\\n", "\n"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExportChooserDialog = false
                        showExportEncryptedDialog = true
                    }) {
                        Text(stringResource(R.string.settings_backup_export_encrypted))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showExportChooserDialog = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        TextButton(onClick = {
                            showExportChooserDialog = false
                            showExportPlainDialog = true
                        }) {
                            Text(stringResource(R.string.settings_backup_export_plain))
                        }
                    }
                },
            )
        }

        if (showExportPlainDialog) {
            AlertDialog(
                onDismissRequest = { showExportPlainDialog = false },
                title = { Text(stringResource(R.string.settings_export_plain_dialog_title)) },
                text = {
                    Text(
                        stringResource(R.string.settings_export_plain_dialog_text_runtime).replace("\\n", "\n"),
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
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }

        if (showExportEncryptedDialog) {
            PassphraseDialog(
                title = stringResource(R.string.settings_export_encrypted_dialog_title),
                message = stringResource(R.string.settings_export_encrypted_dialog_message_runtime).replace("\\n", "\n"),
                onConfirm = { pass ->
                    showExportEncryptedDialog = false
                    pendingExportPassphrase = pass
                    val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    exportLauncher.launch("wakeplan-backup-$ts-encrypted.json")
                },
                onDismiss = { showExportEncryptedDialog = false },
            )
        }

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

        if (showImportConfirmDialog && pendingImportUri != null && pendingImportEncrypted) {
            PassphraseDialog(
                title = stringResource(R.string.settings_import_encrypted_dialog_title),
                message = stringResource(R.string.settings_import_encrypted_dialog_message),
                requireConfirmation = false,
                onConfirm = { pass ->
                    showImportConfirmDialog = false
                    val uri = pendingImportUri
                    if (uri != null) {
                        backupViewModel.import(uri, passphrase = pass)
                    }
                    pendingImportUri = null
                    pendingImportEncrypted = false
                },
                onDismiss = {
                    showImportConfirmDialog = false
                    pendingImportUri = null
                    pendingImportEncrypted = false
                },
            )
        }

        if (showRetentionDialog) {
        RetentionDialog(
            current = retentionDays,
            onSelect = {
                viewModel.setCompletionRetentionDays(it)
                showRetentionDialog = false
            },
            onDismiss = { showRetentionDialog = false },
        )
    }

    if (showLanguageDialog) {
            LanguageChoiceDialog(
                current = currentLocale,
                onSelect = { choice ->
                    showLanguageDialog = false
                    if (choice != viewModel.currentLocale) {
                        viewModel.setLocale(choice)
                        (context as? MainActivity)?.recreate()
                    }
                },
                onDismiss = { showLanguageDialog = false },
            )
        }

        if (showPrivacyPolicy) {
            AlertDialog(
                onDismissRequest = { showPrivacyPolicy = false },
                title = { Text(stringResource(R.string.about_privacy_policy_title)) },
                text = {
                    Text(
                        stringResource(R.string.about_privacy_policy_content),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showPrivacyPolicy = false }) {
                        Text(stringResource(R.string.about_privacy_policy_close))
                    }
                },
            )
        }
    }
}

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
private fun retentionLabel(days: Int): String = when (days) {
    90 -> stringResource(R.string.retention_90_days)
    365 -> stringResource(R.string.retention_1_year)
    else -> stringResource(R.string.retention_forever)
}

@Composable
private fun RetentionDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(0 to R.string.retention_forever, 90 to R.string.retention_90_days, 365 to R.string.retention_1_year)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_retention_title)) },
        text = {
            Column {
                options.forEach { (days, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(days) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = current == days,
                            onClick = { onSelect(days) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.retention_dialog_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_privacy_policy_close)) }
        },
    )
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val appVersionProvider: com.wakeplan.app.data.backup.AppVersionProvider,
    private val localeHelper: LocaleHelper,
    private val scheduleRefresher: com.wakeplan.app.notification.ScheduleRefresher,
    private val recapScheduler: com.wakeplan.app.notification.RecapScheduler,
    private val clock: java.time.Clock,
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

    val completionRetentionDays = preferences.completionRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val versionName: String get() = appVersionProvider.versionName
    val versionCode: Int get() = appVersionProvider.versionCode

    val currentLocale: LocaleHelper.LocaleChoice get() = localeHelper.current

    fun setLocale(choice: LocaleHelper.LocaleChoice) {
        localeHelper.apply(choice)
    }

    // v0.4.1：lead / starting 开关变更后立刻重排闹钟——之前只写 DataStore，
    // 已挂闹钟按旧 lead 照响，关掉开关也照响（cancelAll 空实现叠加）
    fun setLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            preferences.setTaskStartingLeadMinutes(minutes)
            scheduleRefresher.refresh(java.time.LocalDate.now(clock))
        }
    }
    fun setStartingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setTaskStartingEnabled(enabled)
            scheduleRefresher.refresh(java.time.LocalDate.now(clock))
        }
    }
    fun setOverdueEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTaskOverdueEnabled(enabled) }
    }
    fun setCompletedEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTaskCompletedEnabled(enabled) }
    }
    fun setCompletionRetentionDays(days: Int) {
        viewModelScope.launch { preferences.setCompletionRetentionDays(days) }
    }

    // v0.5：开关切换即起停每日回顾调度（Worker 内另有偏好自守）
    fun setRecapEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setPlanRecapEnabled(enabled)
            if (enabled) recapScheduler.start() else recapScheduler.stop()
        }
    }
}
