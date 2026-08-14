package com.wakeplan.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wake Plan — 底部 4 tab 栏（v0.4 视觉刷新）
 *
 * 替代 M3 `NavigationBar`，匹配 design HTML 的玻璃模糊风格：
 * - 背景：`rgba(250,250,248,0.95)`（light）/ `#121A2C`（dark）+ 顶上 0.5px 边线
 * - 4 tab：今天 / 计划 / 统计 / 设置
 * - active tab：icon + label = onSurface
 * - inactive tab：icon + label = onSurfaceVariant
 * - icon: 22dp
 * - label: 10sp medium/semibold, 0.06em letter-spacing
 *
 * 用法（替代 M3 NavigationBar）：
 * ```
 * Scaffold(
 *     bottomBar = { WakePlanBottomBar(currentRoute, onTabClick, tabs) },
 *     ...
 * )
 * ```
 */
@Composable
fun WakePlanBottomBar(
    currentRoute: String?,
    onTabClick: (route: String) -> Unit,
    tabs: List<BottomTabItem>,
    modifier: Modifier = Modifier,
) {
    val isLight = isLightTheme()
    val containerColor = if (isLight) Color(0xF2FAFAF8) else Color(0xFF121A2C)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .border(width = 0.5.dp, color = borderColor)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                BottomTabCell(
                    tab = tab,
                    selected = selected,
                    onClick = { onTabClick(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomTabCell(
    tab: BottomTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.contentDescription,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.6.sp,
            ),
            color = color,
        )
    }
}

/**
 * 简化的 tab 元数据 — 跟 `ui/nav/BottomTab.kt` 的 `BottomTab` enum 解耦
 * 方便 bottom bar 在不同地方复用（不强制 enum）
 */
data class BottomTabItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String? = null,
)

/** 简易亮/暗判断：看主题背景的近似亮度 */
@Composable
private fun isLightTheme(): Boolean {
    val r = MaterialTheme.colorScheme.background.red
    val g = MaterialTheme.colorScheme.background.green
    val b = MaterialTheme.colorScheme.background.blue
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return luminance > 0.5f
}
