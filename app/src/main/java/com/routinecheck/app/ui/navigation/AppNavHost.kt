package com.routinecheck.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.routinecheck.app.ui.chatheadapps.ChatHeadAppsScreen
import com.routinecheck.app.ui.home.HomeScreen
import com.routinecheck.app.ui.memo.MemoScreen
import com.routinecheck.app.ui.routineedit.RoutineEditScreen
import com.routinecheck.app.ui.routines.RoutinesScreen
import com.routinecheck.app.ui.settings.SettingsScreen
import com.routinecheck.app.ui.stats.StatsScreen

object Routes {
    const val HOME = "home"
    const val ROUTINES = "routines"
    const val STATS = "stats"
    const val CHATHEAD_APPS = "chathead_apps"
    const val SETTINGS = "settings"
    const val ROUTINE_EDIT = "routine_edit?routineId={routineId}"
    const val MEMO = "memo/{date}"

    fun routineEdit(routineId: Long? = null) = "routine_edit?routineId=${routineId ?: -1L}"
    fun memo(date: String) = "memo/$date"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "홈", Icons.Rounded.Home),
    Tab(Routes.ROUTINES, "루틴", Icons.Rounded.Alarm),
    Tab(Routes.STATS, "통계", Icons.Rounded.BarChart)
)

@Composable
fun AppRoot(pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // 챗헤드의 "앱 관리"/"설정" 버튼 등으로 특정 화면을 바로 열어야 할 때
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (TABS.any { it.route == currentRoute }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenRoutine = { navController.navigate(Routes.routineEdit(it)) },
                    onNewRoutine = { navController.navigate(Routes.routineEdit()) },
                    onOpenMemo = { navController.navigate(Routes.memo(it)) },
                    onManageChatHeadApps = { navController.navigate(Routes.CHATHEAD_APPS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.ROUTINES) {
                RoutinesScreen(
                    onOpenRoutine = { navController.navigate(Routes.routineEdit(it)) },
                    onNewRoutine = { navController.navigate(Routes.routineEdit()) }
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(
                Routes.ROUTINE_EDIT,
                arguments = listOf(navArgument("routineId") { type = NavType.LongType; defaultValue = -1L })
            ) {
                RoutineEditScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHATHEAD_APPS) {
                ChatHeadAppsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onManageChatHeadApps = { navController.navigate(Routes.CHATHEAD_APPS) }
                )
            }
            composable(
                Routes.MEMO,
                arguments = listOf(navArgument("date") { type = NavType.StringType })
            ) {
                MemoScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
