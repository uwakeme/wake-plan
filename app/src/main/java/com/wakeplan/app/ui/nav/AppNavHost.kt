package com.wakeplan.app.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wakeplan.app.ui.plan.CreatePlanScreen
import com.wakeplan.app.ui.plan.PlanDetailScreen
import com.wakeplan.app.ui.plan.PlanDetailViewModel
import com.wakeplan.app.ui.plan.PlanListScreen
import com.wakeplan.app.ui.plan.SegmentEditorScreen
import com.wakeplan.app.ui.plan.SegmentEditorViewModel
import com.wakeplan.app.ui.settings.SettingsScreen
import com.wakeplan.app.ui.task.TaskEditorScreen
import com.wakeplan.app.ui.task.TaskEditorViewModel
import com.wakeplan.app.ui.stats.StatsScreen
import com.wakeplan.app.ui.template.TemplateListScreen
import com.wakeplan.app.ui.today.TodayScreen

/**
 * 根导航 — Scaffold 内嵌 BottomBar + NavHost
 *
 * 路由表（v0.1 完成 + Phase 5.2.1 加 stats）：
 *   today                                                            → TodayScreen
 *   plans                                                            → PlanListScreen
 *   stats                                                            → StatsScreen
 *   plans/create                                                     → CreatePlanScreen
 *   plans/{planId}                                                   → PlanDetailScreen
 *   plans/{planId}/segments/new                                      → SegmentEditorScreen (create)
 *   plans/{planId}/segments/{segmentId}                              → SegmentEditorScreen (edit)
 *   plans/{planId}/segments/{segmentId}/tasks/new                    → TaskEditorScreen (create)
 *   plans/{planId}/segments/{segmentId}/tasks/{taskId}               → TaskEditorScreen (edit)
 *   settings                                                         → SettingsScreen
 *   settings/templates                                               → TemplateListScreen
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in TOP_LEVEL_ROUTES) {
                NavigationBar {
                    BottomTab.entries.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavGraph(
            navController = navController,
            innerPadding = innerPadding,
        )
    }
}

private val TOP_LEVEL_ROUTES: Set<String> = BottomTab.entries.map { it.route }.toSet()

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = BottomTab.Today.route,
        modifier = Modifier.padding(innerPadding),
    ) {
        // Top-level tabs
        composable(BottomTab.Today.route) { TodayScreen() }
        composable(BottomTab.Plans.route) {
            PlanListScreen(
                onCreatePlan = { navController.navigate(Route.PlanCreate) },
                onPlanClick = { plan -> navController.navigate(Route.planDetail(plan.id)) },
            )
        }
        composable(BottomTab.Stats.route) {
            StatsScreen()
        }
        composable(BottomTab.Settings.route) {
            SettingsScreen(
                onTemplatesClick = { navController.navigate(Route.Templates) },
            )
        }

        // Plans 子路由
        composable(Route.PlanCreate) {
            CreatePlanScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.PlanDetailPattern,
            arguments = listOf(
                navArgument(PlanDetailViewModel.ARG_PLAN_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val planId = entry.arguments?.getString(PlanDetailViewModel.ARG_PLAN_ID).orEmpty()
            PlanDetailScreen(
                onBack = { navController.popBackStack() },
                onAddSegment = { navController.navigate(Route.segmentEditorCreate(planId)) },
                onSegmentClick = { segmentId ->
                    navController.navigate(Route.segmentEditorEdit(planId, segmentId))
                },
                onAddTask = { segmentId ->
                    navController.navigate(Route.taskEditorCreate(planId, segmentId))
                },
                onTaskClick = { segmentId, taskId ->
                    navController.navigate(Route.taskEditorEdit(planId, segmentId, taskId))
                },
            )
        }
        composable(
            route = Route.SegmentEditorCreatePattern,
            arguments = listOf(
                navArgument(PlanDetailViewModel.ARG_PLAN_ID) { type = NavType.StringType },
            ),
        ) {
            SegmentEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.SegmentEditorEditPattern,
            arguments = listOf(
                navArgument(PlanDetailViewModel.ARG_PLAN_ID) { type = NavType.StringType },
                navArgument(SegmentEditorViewModel.ARG_SEGMENT_ID) { type = NavType.StringType },
            ),
        ) {
            SegmentEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.TaskEditorCreatePattern,
            arguments = listOf(
                navArgument(PlanDetailViewModel.ARG_PLAN_ID) { type = NavType.StringType },
                navArgument(SegmentEditorViewModel.ARG_SEGMENT_ID) { type = NavType.StringType },
            ),
        ) {
            TaskEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.TaskEditorEditPattern,
            arguments = listOf(
                navArgument(PlanDetailViewModel.ARG_PLAN_ID) { type = NavType.StringType },
                navArgument(SegmentEditorViewModel.ARG_SEGMENT_ID) { type = NavType.StringType },
                navArgument(TaskEditorViewModel.ARG_TASK_ID) { type = NavType.StringType },
            ),
        ) {
            TaskEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        // Settings 子路由
        composable(Route.Templates) {
            TemplateListScreen(
                onBack = { navController.popBackStack() },
                onTemplateClick = { /* Phase 1.5 / 2.0 实装"从模板新建" */ },
            )
        }
    }
}

/** 集中管理所有路由常量 */
object Route {
    const val PlanCreate = "plans/create"
    const val PlanDetailPattern = "plans/{planId}"
    const val SegmentEditorCreatePattern = "plans/{planId}/segments/new"
    const val SegmentEditorEditPattern = "plans/{planId}/segments/{segmentId}"
    const val TaskEditorCreatePattern = "plans/{planId}/segments/{segmentId}/tasks/new"
    const val TaskEditorEditPattern = "plans/{planId}/segments/{segmentId}/tasks/{taskId}"
    const val Templates = "settings/templates"

    fun planDetail(planId: String) = "plans/$planId"
    fun segmentEditorCreate(planId: String) = "plans/$planId/segments/new"
    fun segmentEditorEdit(planId: String, segmentId: String) = "plans/$planId/segments/$segmentId"
    fun taskEditorCreate(planId: String, segmentId: String) =
        "plans/$planId/segments/$segmentId/tasks/new"
    fun taskEditorEdit(planId: String, segmentId: String, taskId: String) =
        "plans/$planId/segments/$segmentId/tasks/$taskId"
}
