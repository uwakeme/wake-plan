package com.wakeplan.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Wake Plan — 通用顶部栏
 *
 * 替代 M3 TopAppBar 的"title" 槽位，注入更结构化的内容：
 * - 主标题（20-22sp semibold）
 * - 可选副标题（13sp text-tertiary，4dp top margin）
 * - 可选侧边动作（pill 按钮 / icon button）
 *
 * 用法（嵌在 Scaffold.topBar 里）：
 * ```
 * TopAppBar(title = { TopHeaderContent("我的计划", subtitle = "3 个活跃计划", sideAction = { ... }) })
 * ```
 *
 * 或者直接当 Scaffold topBar 用 `TopHeaderScaffold(...)`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeaderContent(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sideAction: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (sideAction != null) {
            sideAction()
        }
    }
}

/**
 * 包装的 TopAppBar — 把 [TopHeaderContent] 装进 M3 TopAppBar 的 title 槽位
 *
 * 用法（推荐）：
 * ```
 * Scaffold(
 *     topBar = { TopAppBar(title = { TopHeaderContent("我的计划", subtitle = "3 个活跃计划") }) },
 *     ...
 * )
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarWithHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sideAction: @Composable (() -> Unit)? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
    ),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            TopHeaderContent(
                title = title,
                subtitle = subtitle,
                sideAction = sideAction,
            )
        },
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}
