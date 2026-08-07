package com.wakeplan.app.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.wakeplan.app.R

/**
 * 底部 3 tab — 对应 `design/partials/project-shell.html` 的 现在 / 计划 / 设置
 */
enum class BottomTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Today(
        route = "today",
        labelRes = R.string.tab_today,
        icon = Icons.Outlined.AccessTime,
    ),
    Plans(
        route = "plans",
        labelRes = R.string.tab_plans,
        icon = Icons.Outlined.GridView,
    ),
    Settings(
        route = "settings",
        labelRes = R.string.tab_settings,
        icon = Icons.Outlined.Settings,
    );

    companion object {
        fun fromRoute(route: String?): BottomTab? =
            entries.firstOrNull { it.route == route }
    }
}
