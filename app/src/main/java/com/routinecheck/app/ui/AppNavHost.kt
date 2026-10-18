package com.routinecheck.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.routinecheck.app.ui.common.CoachMarkHost
import com.routinecheck.app.ui.common.CoachMarkState
import com.routinecheck.app.ui.common.coachTarget
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
import com.routinecheck.app.ui.chatheaditems.ChatHeadItemsScreen
import com.routinecheck.app.ui.home.HomeScreen
import com.routinecheck.app.ui.memo.MemosScreen
import com.routinecheck.app.ui.routine.RoutineEditScreen
import com.routinecheck.app.ui.routine.RoutinesScreen
import com.routinecheck.app.ui.settings.SettingsScreen
import com.routinecheck.app.ui.stats.StatsScreen
import java.time.LocalDate

object Routes {
    const val HOME = "home"
    const val ROUTINES = "routines"
    const val STATS = "stats"
    const val CHATHEAD_ITEMS = "chathead_items"
    const val SETTINGS = "settings"
    const val MEMOS = "memos"
    /** 루틴/일정 만들기·수정. date 가 있으면 그 날짜의 새 일정 (v0.5.0) */
    const val ROUTINE_EDIT = "routine_edit?routineId={routineId}&date={date}"

    fun routineEdit(routineId: Long? = null) = "routine_edit?routineId=${routineId ?: -1L}"

    /** 새 일정 (yyyy-MM-dd 날짜 기준) */
    fun scheduleNew(date: LocalDate) = "routine_edit?routineId=-1&date=$date"
}

private const val KEY_OPEN_MEMO_DATE = "openMemoDate"

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

    // v0.6.0 코치마크 가이드: 앱 전체에서 하나의 상태를 공유
    val context = LocalContext.current
    val coachMarks = remember {
        CoachMarkState(context.applicationContext.getSharedPreferences(CoachMarkState.PREFS_NAME, Context.MODE_PRIVATE))
    }

    CoachMarkHost(coachMarks) {
    Scaffold(
        bottomBar = {
            if (TABS.any { it.route == currentRoute }) {
                NavigationBar(Modifier.coachTarget("nav.tabs")) {
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
            composable(Routes.HOME) { entry ->
                // 전체 메모 화면에서 "홈에서 보기"로 넘겨준 날짜
                val requested by entry.savedStateHandle.getStateFlow<String?>(KEY_OPEN_MEMO_DATE, null).collectAsState()
                HomeScreen(
                    onOpenAllMemos = { navController.navigate(Routes.MEMOS) },
                    requestedMemoDate = requested?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    onRequestedMemoHandled = { entry.savedStateHandle[KEY_OPEN_MEMO_DATE] = null },
                    onOpenRoutine = { navController.navigate(Routes.routineEdit(it)) },
                    onNewRoutine = { navController.navigate(Routes.routineEdit()) },
                    onNewSchedule = { navController.navigate(Routes.scheduleNew(it)) },
                    onManageChatHeadItems = { navController.navigate(Routes.CHATHEAD_ITEMS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.ROUTINES) {
                RoutinesScreen(
                    onOpenRoutine = { navController.navigate(Routes.routineEdit(it)) },
                    onNewRoutine = { navController.navigate(Routes.routineEdit()) },
                    onNewSchedule = { navController.navigate(Routes.scheduleNew(LocalDate.now())) }
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(
                Routes.ROUTINE_EDIT,
                arguments = listOf(
                    navArgument("routineId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                RoutineEditScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHATHEAD_ITEMS) {
                ChatHeadItemsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.MEMOS) {
                MemosScreen(
                    onBack = { navController.popBackStack() },
                    onOpenInHome = { date ->
                        navController.getBackStackEntry(Routes.HOME).savedStateHandle[KEY_OPEN_MEMO_DATE] = date.toString()
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onManageChatHeadItems = { navController.navigate(Routes.CHATHEAD_ITEMS) },
                    onReplayGuide = {
                        // 모든 화면의 가이드를 처음 상태로 돌리고 홈에서 다시 시작
                        coachMarks.resetAll()
                        navController.navigateTab(Routes.HOME)
                    }
                )
            }
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
