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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wakeplan.app.ui.plan.CreatePlanScreen
import com.wakeplan.app.ui.plan.PlanListScreen
import com.wakeplan.app.ui.settings.SettingsScreen
import com.wakeplan.app.ui.today.TodayScreen

/**
 * 根导航 — Scaffold 内嵌 BottomBar + NavHost
 *
 * 路由表：
 *   today                → TodayScreen（Phase 0 占位，Phase 2 接入当前时刻聚合）
 *   plans                → PlanListScreen
 *   plans/create         → CreatePlanScreen
 *   plans/{planId}       → PlanDetailScreen（Phase 1.3 接入）
 *   settings             → SettingsScreen（Phase 0 占位）
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // 仅在顶级 tab 路由显示 BottomBar；进入 plans/create 等子路由时隐藏
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
                onPlanClick = { /* Phase 1.3 接入 PlanDetail */ },
            )
        }
        composable(BottomTab.Settings.route) { SettingsScreen() }

        // 子路由
        composable(Route.PlanCreate) {
            CreatePlanScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}

/** 集中管理所有路由常量 */
object Route {
    const val PlanCreate = "plans/create"

    fun planDetail(planId: String) = "plans/$planId"
}
