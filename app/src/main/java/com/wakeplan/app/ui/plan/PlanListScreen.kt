package com.wakeplan.app.ui.plan

import com.wakeplan.app.ui.common.WpRadius


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.ui.common.PlanCard

/**
 * 计划列表屏 — v0.4 视觉刷新
 *
 * - 顶部 TopAppBar：标题 + 副标题 "X 个活跃计划"
 * - FAB：跳新建
 * - 列表 / 空态
 *
 * 卡片渲染用 [PlanCard]（从 ui/common/ 抽出来，superdesign 验证过），
 * 数据用 [PlanListViewModel.plansWithStats]（plan + task + completion 合并的 view-model）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanListScreen(
    onCreatePlan: () -> Unit,
    onPlanClick: (Plan) -> Unit,
    viewModel: PlanListViewModel = hiltViewModel(),
) {
    val plansWithStats by viewModel.plansWithStats.collectAsStateWithLifecycle()
    val activeCount = plansWithStats.count { it.status != PlanStatus.IDLE }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.tab_plans),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        if (plansWithStats.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.plan_list_active_count, activeCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreatePlan) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.plan_list_cd_new))
            }
        },
    ) { padding ->
        if (plansWithStats.isEmpty()) {
            EmptyPlansState(modifier = Modifier.padding(padding))
        } else {
            PlansList(
                plansWithStats = plansWithStats,
                onPlanClick = onPlanClick,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun PlansList(
    plansWithStats: List<PlanCardData>,
    onPlanClick: (Plan) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp, // 给 FAB 留位
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(plansWithStats, key = { it.plan.id }) { data ->
            PlanCard(
                data = data,
                onClick = { onPlanClick(data.plan) },
                index = plansWithStats.indexOf(data).coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun EmptyPlansState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_list_empty_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.plan_list_empty_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
