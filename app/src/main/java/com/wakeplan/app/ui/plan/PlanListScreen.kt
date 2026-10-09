package com.wakeplan.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakeplan.app.R
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.ui.common.PlanCard
import com.wakeplan.app.ui.common.WpRadius

@Composable
fun PlanListScreen(
    onCreatePlan: () -> Unit,
    onPlanClick: (Plan) -> Unit,
    viewModel: PlanListViewModel = hiltViewModel(),
) {
    val plansWithStats by viewModel.plansWithStats.collectAsStateWithLifecycle()
    val archivedPlans by viewModel.archivedPlans.collectAsStateWithLifecycle()
    var showArchived by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (plansWithStats.isEmpty() && archivedPlans.isEmpty()) {
            EmptyPlansState()
        } else {
            PlansList(
                plansWithStats = plansWithStats,
                archivedPlans = archivedPlans,
                showArchived = showArchived,
                onToggleArchivedSection = { showArchived = !showArchived },
                onPlanClick = onPlanClick,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.tab_plans),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.04).sp,
                lineHeight = 38.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            FloatingActionButton(
                onClick = onCreatePlan,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF090E1A),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 4.dp,
                ),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.plan_list_cd_new),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PlansList(
    plansWithStats: List<PlanCardData>,
    archivedPlans: List<PlanCardData>,
    showArchived: Boolean,
    onToggleArchivedSection: () -> Unit,
    onPlanClick: (Plan) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 44.dp + 56.dp + 16.dp,
            bottom = 100.dp,
            start = 20.dp,
            end = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(
            plansWithStats,
            key = { _, data -> "active-${data.plan.id}" },
        ) { index, data ->
            PlanCard(
                data = data,
                onClick = { onPlanClick(data.plan) },
                index = index,
            )
        }

        if (archivedPlans.isNotEmpty()) {
            item("archived-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(WpRadius.md))
                        .clickable(onClick = onToggleArchivedSection)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.plan_list_archived_section, archivedPlans.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (showArchived) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (showArchived) {
                items(archivedPlans, key = { "archived-${it.plan.id}" }) { data ->
                    PlanCard(
                        data = data,
                        onClick = { onPlanClick(data.plan) },
                        index = 0,
                        modifier = Modifier.alpha(0.55f),
                    )
                }
            }
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
