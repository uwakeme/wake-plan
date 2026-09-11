package com.wakeplan.app.widget

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.wakeplan.app.MainActivity
import com.wakeplan.app.R
import com.wakeplan.app.ui.common.NavEventBus

/**
 * Wake Plan 快捷设置 Tile — Phase 5.7
 *
 * 行为：
 * - 点击 tile → startActivityAndCollapse(Intent(MainActivity)) + emit NavEventBus.NavToToday
 *   → MainActivity 启动后跳 Today tab
 * - 状态：简单 v0.2 (Active when 有今日 PENDING task, Inactive otherwise)
 *   让用户知道下拉里 tile 状态有意义
 *
 * 生命周期：
 * - onStartListening：系统把 tile 放到下拉面板时调用；这里拉数据更新 tile 状态
 * - onStopListening：用户收起面板时调用；释放资源
 * - onClick：用户点击
 *
 * Android 14+ 要求 TileService 必须在 manifest 加 permission / meta-data
 */
class WakePlanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // v0.2 简化：直接 Active；v0.3 接 PlanRepository + TaskCompletionRepository
        // 拉今日 PENDING 任务数决定 Active/Inactive
        qsTile?.apply {
            label = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.tile_subtitle_open_today)
            }
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // 1. 通过 NavEventBus 通知 Compose 跳 Today（app 已在跑时）
        NavEventBus.emit(NavEventBus.Event.NavToToday)
        // 2. 启动 MainActivity（app 没在跑时冷启动）
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(TodayWidgetProvider.EXTRA_NAV_TO_TODAY, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 必须用 startActivityAndCollapse（普通 startActivity 在 quick settings 抛异常）
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            // API 29~33 只有 Intent 重载可用（PendingIntent 版本是 API 34+ 的）
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(launchIntent)
        }
    }
}
