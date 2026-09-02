package com.wakeplan.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.notification.NotificationBuilder
import com.wakeplan.app.ui.common.HighlightBus
import com.wakeplan.app.ui.common.NavEventBus
import com.wakeplan.app.ui.nav.AppNavHost
import com.wakeplan.app.ui.onboarding.OnboardingScreen
import com.wakeplan.app.ui.splash.BrandedSplash
import com.wakeplan.app.ui.theme.WakePlanTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 唯一 Activity — Compose 持有整个导航
 * @AndroidEntryPoint 注入 Hilt
 *
 * v0.4 启动动画（两层 splash）：
 * 1. 系统 splash（Theme.WakePlan.Splash，静态日出图标）：只负责冷启动防白屏，
 *    installSplashScreen() 完成 postSplashScreenTheme 切换后由系统按默认行为移除。
 *    【不要】在这里设 setKeepOnScreenCondition 做纯定时 —— condition 的重评估依赖
 *    splash view 重绘，view 静止后永远不触发；也【不要】设 setOnExitAnimationListener
 *    除非动画结束手动 remove()，否则 API 31+ 会永久遮挡。
 * 2. Compose 层 [BrandedSplash] overlay：盖在内容上播 1.4s 日出动画后淡出，
 *    时序完全自控，不依赖系统回调。
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
        // 必须在 super.onCreate() 之前调用 — 接管系统 SplashScreen 做主题切换
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 系统栏全透明 + 暗色图标策略（强制深色主题）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // 处理 deep link（应用已运行时收到 PendingIntent 走 onNewIntent；冷启动走 onCreate intent）
        handleDeepLink(intent)

        setContent {
            WakePlanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRootWithSplash()
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

/**
 * 内容根 + 品牌启动 overlay。
 * overlay 始终盖在最上层（Onboarding / AppNavHost 之上），淡出后从 composition 移除。
 */
@Composable
private fun AppRootWithSplash() {
    var showBrandedSplash by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        AppRoot()
        if (showBrandedSplash) {
            BrandedSplash(onFinished = { showBrandedSplash = false })
        }
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
