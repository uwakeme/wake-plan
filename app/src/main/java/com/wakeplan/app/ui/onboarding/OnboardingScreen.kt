package com.wakeplan.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wakeplan.app.notification.NotificationPermissionChecker

/**
 * 启动引导页 — Phase 3.3
 *
 * 3 步：
 * 1. 欢迎 — Wake Plan 是什么
 * 2. 通知权限 — Android 13+ 必须申请 POST_NOTIFICATIONS
 * 3. 完成 — 引导用户去开启权限（如果拒绝）
 *
 * MainActivity 在 [com.wakeplan.app.data.prefs.UserPreferences.hasShownOnboarding] 为 false 时显示此页
 * 用户点"开始使用"后 setHasShownOnboarding(true) 跳转 AppNavHost
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var notificationGranted by remember {
        mutableStateOf(NotificationPermissionChecker.hasPostNotificationsPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted
        if (granted) {
            onRequestNotificationPermission()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            // 顶部进度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .padding(horizontal = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape = androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                    if (i < 2) Spacer(Modifier.size(8.dp))
                }
            }

            Spacer(Modifier.height(48.dp))

            when (step) {
                0 -> StepWelcome()
                1 -> StepNotificationPermission(
                    granted = notificationGranted,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationGranted = true
                        }
                    },
                )
                2 -> StepFinish(notificationGranted = notificationGranted)
            }

            Spacer(Modifier.weight(1f))

            // 底部按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }) {
                        Text("上一步")
                    }
                } else {
                    Spacer(Modifier)
                }
                Button(
                    onClick = {
                        if (step < 2) {
                            step++
                        } else {
                            onFinished()
                        }
                    },
                ) {
                    Text(if (step < 2) "下一步" else "开始使用")
                }
            }
        }
    }
}

@Composable
private fun StepWelcome() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "👋",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "欢迎使用 Wake Plan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "把健身 / 学习 / 副业分开管理。\n到了时间点，App 会准时提醒你。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StepNotificationPermission(
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "🔔",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "开启通知",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "任务开始前 5 分钟、任务超时未完成时，\nWake Plan 会发本地通知提醒你。\n所有通知在本地处理，不会上传到云端。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!granted) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRequest) {
                Text("申请通知权限")
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "✓ 通知权限已开启",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StepFinish(notificationGranted: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "🎉",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "准备就绪",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (notificationGranted) {
                "通知权限已开启，可以开始创建你的第一个计划了。"
            } else {
                "通知权限未开启（你随时可以在「设置」里开启）。\n点下方按钮继续。"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// helper extension for `Box.background(color, shape)`
//（import 已含 foundation.background，直接用 Modifier.background(color, shape) 即可）
