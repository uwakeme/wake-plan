package com.wakeplan.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.notification.NotificationBuilder
import com.wakeplan.app.ui.common.HighlightBus
import com.wakeplan.app.ui.common.NavEventBus
import com.wakeplan.app.ui.nav.AppNavHost
import com.wakeplan.app.ui.onboarding.OnboardingScreen
import com.wakeplan.app.ui.theme.WakePlanTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 唯一 Activity — Compose 持有整个导航
 * @AndroidEntryPoint 注入 Hilt
 *
 * Phase 3.3：
 * - 首次启动走 OnboardingScreen（hasShownOnboarding=false）
 * - 完成后走 AppNavHost
 * - deep link（通知点击）由 NotificationBuilder PendingIntent 启动 → extras → HighlightBus.emit
 *   → TodayScreen collect → 滚动 + 高亮对应任务
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 处理 deep link（应用已运行时收到 PendingIntent 走 onNewIntent；冷启动走 onCreate intent）
        handleDeepLink(intent)
        setContent {
            WakePlanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        // 5.6 widget 入口：跳 Today tab
        if (intent.getBooleanExtra(com.wakeplan.app.widget.TodayWidgetProvider.EXTRA_NAV_TO_TODAY, false)) {
            NavEventBus.emit(NavEventBus.Event.NavToToday)
            intent.removeExtra(com.wakeplan.app.widget.TodayWidgetProvider.EXTRA_NAV_TO_TODAY)
        }
        // 通知 deep link：跳到对应 task 高亮
        val taskId = intent.getStringExtra(NotificationBuilder.EXTRA_TASK_ID) ?: return
        HighlightBus.emit(taskId)
        intent.removeExtra(NotificationBuilder.EXTRA_TASK_ID)
        intent.removeExtra(NotificationBuilder.EXTRA_COMPLETION_ID)
    }
}

@Composable
private fun AppRoot() {
    val viewModel: AppRootViewModel = hiltViewModel()
    // 初始 true — 未读出 prefs 前先显示 onboarding（避免 splash 后闪 AppNavHost）
    val shouldShowOnboarding by viewModel.shouldShowOnboarding.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()

    if (shouldShowOnboarding) {
        OnboardingScreen(
            onFinished = {
                scope.launch { viewModel.markOnboardingDone() }
            },
        )
    } else {
        AppNavHost()
    }
}
