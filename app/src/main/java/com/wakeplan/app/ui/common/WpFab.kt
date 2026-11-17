package com.wakeplan.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 统一的主操作 FAB（加任务 / 加计划）— 各页共用，样式只收在这里一处
 *
 * M3 默认 56dp 圆角方形 + primary/onPrimary 配色；调用方挂在自己
 * Scaffold 的 floatingActionButton 槽（右下角、底栏上方 16dp）。
 */
@Composable
fun WpAddFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = contentDescription)
    }
}
