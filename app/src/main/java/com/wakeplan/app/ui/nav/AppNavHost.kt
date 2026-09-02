package com.wakeplan.app.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wakeplan.app.ui.common.BottomTabItem
import com.wakeplan.app.ui.common.NavEventBus
import com.wakeplan.app.ui.common.WakePlanBottomBar
import com.wakeplan.app.ui.plan.CreatePlanScreen
import com.wakeplan.app.ui.plan.CreatePlanViewModel
import com.wakeplan.app.ui.plan.PlanDetailScreen
import com.wakeplan.app.ui.plan.PlanDetailViewModel
import com.wakeplan.app.ui.plan.PlanListScreen
import com.wakeplan.app.ui.settings.SettingsScreen
import com.wakeplan.app.ui.task.TaskEditorScreen
import com.wakeplan.app.ui.task.TaskEditorViewModel
import com.wakeplan.app.ui.stats.StatsScreen
import com.wakeplan.app.ui.template.TemplateDetailScreen
import com.wakeplan.app.ui.template.TemplateDetailViewModel
import com.wakeplan.app.ui.template.TemplateListScreen
import com.wakeplan.app.ui.today.TodayScreen

/**
 * 根导航 — Scaffold 内嵌 BottomBar + NavHost
 *
 * 路由表（v0.3 扁平化 — 砍段）：
 *   today                                          → TodayScreen
 *   plans                                          → PlanListScreen
 *   stats                                          → StatsScreen
 *   plans/create                                   → CreatePlanScreen
 *   plans/{planId}                                 → PlanDetailScreen
 *   plans/{planId}/tasks/new                       → TaskEditorScreen (create)
 *   plans/{planId}/tasks/{taskId}                  → TaskEditorScreen (edit)
 *   settings                                       → SettingsScreen
 *   settings/templates                             → TemplateListScreen
 *   settings/templates/{templateId}                → TemplateDetailScreen
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Phase 5.6: 跨 Activity 边界的导航指令 — widget / Quick Settings Tile 触发
    LaunchedEffect(Unit) {
        NavEventBus.events.collect { event ->
            when (event) {
                is NavEventBus.Event.NavToToday -> {
                    navController.navigate(BottomTab.Today.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in TOP_LEVEL_ROUTES) {
                WakePlanBottomBar(
                    currentRoute = currentRoute,
                    onTabClick = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    tabs = bottomTabItems(),
                )
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

/**
 * 把 [BottomTab] enum 映射成 [BottomTabItem]，供 [WakePlanBottomBar] 用
 *
 * 这里集中做"图标 + 标签字符串"的转换，避免在 BottomBar 内做 stringResource() 调用
 * （M3 体系里 stringResource 是 @Composable 函数，必须在 Composable 上下文内调）
 */
@Composable
private fun bottomTabItems(): List<BottomTabItem> = BottomTab.entries.map { tab ->
    BottomTabItem(
        route = tab.route,
        label = androidx.compose.ui.res.stringResource(tab.labelRes),
        icon = tab.icon,
        contentDescription = androidx.compose.ui.res.stringResource(tab.labelRes),
    )
}

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
                onEditPlan = { navController.navigate(Route.planEdit(planId)) },
                onAddTask = { navController.navigate(Route.taskEditorCreate(planId)) },
                onTaskClick = { taskId ->
                    navController.navigate(Route.taskEditorEdit(planId, taskId))
                },
            )
        }
        composable(
            route = Route.PlanEditPattern,
            arguments = listOf(
                navArgument(CreatePlanViewModel.ARG_PLAN_ID) { type = NavType.StringType },
            ),
        ) {
            CreatePlanScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Route.TaskEditorCreatePattern,
            arguments = listOf(
                navArgument(PlanDetailViewModel.ARG_PLAN_ID) { type = NavType.StringType },
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
                onTemplateClick = { tpl -> navController.navigate(Route.templateDetail(tpl.id)) },
            )
        }
        composable(
            route = Route.TemplateDetailPattern,
            arguments = listOf(
                navArgument(TemplateDetailViewModel.ARG_TEMPLATE_ID) { type = NavType.StringType },
            ),
        ) {
            TemplateDetailScreen(
                onBack = { navController.popBackStack() },
                onApplied = { newPlanId ->
                    // 应用模板成功 → 跳到新 plan 详情；先 popBackStack TemplateList，再 navigate
                    navController.popBackStack(route = Route.Templates, inclusive = false)
                    navController.navigate(Route.planDetail(newPlanId))
                },
            )
        }
    }
}

/** 集中管理所有路由常量 */
object Route {
    const val PlanCreate = "plans/create"
    const val PlanDetailPattern = "plans/{planId}"
    const val PlanEditPattern = "plans/{planId}/edit"
    const val TaskEditorCreatePattern = "plans/{planId}/tasks/new"
    const val TaskEditorEditPattern = "plans/{planId}/tasks/{taskId}"
    const val Templates = "settings/templates"
    const val TemplateDetailPattern = "settings/templates/{templateId}"

    fun planDetail(planId: String) = "plans/$planId"
    fun planEdit(planId: String) = "plans/$planId/edit"
    fun taskEditorCreate(planId: String) = "plans/$planId/tasks/new"
    fun taskEditorEdit(planId: String, taskId: String) = "plans/$planId/tasks/$taskId"
    fun templateDetail(templateId: String) = "settings/templates/$templateId"
}
