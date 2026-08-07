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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wakeplan.app.ui.plan.PlanListScreen
import com.wakeplan.app.ui.settings.SettingsScreen
import com.wakeplan.app.ui.today.TodayScreen

/**
 * 根导航 — Scaffold 内嵌 BottomBar + NavHost
 * Phase 0 只有 3 个 tab；Phase 1+ 加计划详情 / 任务编辑 / 模板库
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        AppNavGraph(
            navController = navController,
            innerPadding = innerPadding,
            modifier = Modifier,
        )
    }
}

@Composable
private fun AppNavGraph(
    navController: androidx.navigation.NavHostController,
    innerPadding: PaddingValues,
    modifier: Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = BottomTab.Today.route,
        modifier = modifier.padding(innerPadding),
    ) {
        composable(BottomTab.Today.route) { TodayScreen() }
        composable(BottomTab.Plans.route) { PlanListScreen() }
        composable(BottomTab.Settings.route) { SettingsScreen() }
    }
}
