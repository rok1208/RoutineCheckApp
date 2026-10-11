package com.routinecheck.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.chathead.ChatHeadController
import com.routinecheck.app.data.local.entity.ChatHeadAppEntity
import com.routinecheck.app.data.repository.Badge
import com.routinecheck.app.data.repository.ChatHeadAppRepository
import com.routinecheck.app.data.repository.ChatHeadSettings
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.data.repository.MemoRepository
import com.routinecheck.app.data.repository.NotificationBadgeRepository
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.data.repository.TodayRepository
import com.routinecheck.app.data.repository.TodaySnapshot
import com.routinecheck.app.domain.RoutineSchedule
import com.routinecheck.app.util.DateKey
import com.routinecheck.app.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 주간 달력 한 칸 */
data class WeekDayCell(
    val date: LocalDate,
    val isToday: Boolean,
    val hasMemo: Boolean,
    val routineCount: Int
)

data class PermissionWarnings(
    val overlayMissing: Boolean = false,
    val notificationMissing: Boolean = false,
    val exactAlarmMissing: Boolean = false,
    val listenerMissing: Boolean = false
) {
    val requiredMissing: Boolean get() = overlayMissing || notificationMissing || exactAlarmMissing
}

data class HomeUiState(
    val today: TodaySnapshot? = null,
    val settings: ChatHeadSettings = ChatHeadSettings(),
    val pinnedApps: List<ChatHeadAppEntity> = emptyList(),
    val badges: Map<String, Badge> = emptyMap(),
    val week: List<WeekDayCell> = emptyList(),
    val permissions: PermissionWarnings = PermissionWarnings()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    todayRepository: TodayRepository,
    private val routineRepository: RoutineRepository,
    private val settingsRepository: ChatHeadSettingsRepository,
    chatHeadAppRepository: ChatHeadAppRepository,
    badgeRepository: NotificationBadgeRepository,
    memoRepository: MemoRepository
) : ViewModel() {

    private val weekStart = DateKey.weekStart(LocalDate.now())
    private val permissions = MutableStateFlow(readPermissions())

    private val week = combine(
        memoRepository.observeMemoDates(weekStart, weekStart.plusDays(6)),
        routineRepository.getRoutines()
    ) { memoDates, routines ->
        (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            WeekDayCell(
                date = date,
                isToday = date == LocalDate.now(),
                hasMemo = date in memoDates,
                routineCount = routines.count { it.isEnabled && RoutineSchedule.isScheduledOn(it.daysOfWeek, date) }
            )
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        todayRepository.observeToday(),
        settingsRepository.observeSettings(),
        chatHeadAppRepository.observeApps(),
        combine(badgeRepository.observeBadges(), week) { b, w -> b to w },
        permissions
    ) { today, settings, pinned, (badges, weekCells), perms ->
        HomeUiState(today, settings, pinned, badges, weekCells, perms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 설정 화면 등에서 돌아왔을 때 권한 상태 다시 읽기 */
    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    fun setChatHeadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnabled(enabled) // 먼저 저장해야 서비스가 켜자마자 꺼지지 않음
            if (enabled) ChatHeadController.show(context) else ChatHeadController.hide(context)
        }
    }

    fun toggleCheck(routineId: Long, packageName: String, checked: Boolean) {
        viewModelScope.launch {
            routineRepository.setChecked(routineId, packageName, DateKey.today(), checked)
        }
    }

    private fun readPermissions() = PermissionWarnings(
        overlayMissing = !PermissionUtils.hasOverlayPermission(context),
        notificationMissing = !PermissionUtils.hasNotificationPermission(context),
        exactAlarmMissing = !PermissionUtils.canScheduleExactAlarms(context),
        listenerMissing = !PermissionUtils.hasNotificationListenerAccess(context)
    )
}
