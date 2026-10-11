package com.routinecheck.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.routinecheck.app.chathead.ChatHeadController
import com.routinecheck.app.data.repository.Badge
import com.routinecheck.app.data.repository.ChatHeadItemRepository
import com.routinecheck.app.data.repository.ChatHeadSettings
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.data.repository.MemoRepository
import com.routinecheck.app.data.repository.NotificationBadgeRepository
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.data.repository.TodayRepository
import com.routinecheck.app.data.repository.TodaySnapshot
import com.routinecheck.app.data.repository.toPinned
import com.routinecheck.app.domain.ChatHeadEntry
import com.routinecheck.app.domain.ChatHeadOrdering
import com.routinecheck.app.domain.DateKey
import com.routinecheck.app.domain.RoutineSchedule
import com.routinecheck.app.system.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** 주간 달력 한 칸 */
data class WeekDayCell(
    val date: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasMemo: Boolean,
    val routineCount: Int
)

data class PermissionWarnings(
    val overlayMissing: Boolean = false,
    val notificationMissing: Boolean = false,
    val exactAlarmMissing: Boolean = false,
    val listenerMissing: Boolean = false
) {
    /** v5부터 필수 권한은 오버레이뿐 (알림·정확한 알람은 선택) */
    val requiredMissing: Boolean get() = overlayMissing
}

/** 홈 화면 안의 메모지 (요일을 누르면 열림) */
data class MemoPanelState(
    val date: LocalDate,
    val open: Boolean = false,
    val text: String = "",
    val loaded: Boolean = false,
    val saved: Boolean = true
)

data class HomeUiState(
    /** 오늘 기준 (챗헤드 카드의 "진행 중 루틴" 표시용) */
    val today: TodaySnapshot? = null,
    /** 주간 달력에서 고른 날짜의 루틴 (기본은 오늘) */
    val day: TodaySnapshot? = null,
    val settings: ChatHeadSettings = ChatHeadSettings(),
    /** 챗헤드 최상단에 보이는 직접 넣은 항목 (폴더·앱·웹사이트) */
    val pinnedEntries: List<ChatHeadEntry> = emptyList(),
    val badges: Map<String, Badge> = emptyMap(),
    val week: List<WeekDayCell> = emptyList(),
    /** 보고 있는 주: 0 = 이번 주, 1 = 다음 주, -1 = 지난주 … */
    val weekOffset: Long = 0,
    val permissions: PermissionWarnings = PermissionWarnings()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todayRepository: TodayRepository,
    private val routineRepository: RoutineRepository,
    private val settingsRepository: ChatHeadSettingsRepository,
    chatHeadItemRepository: ChatHeadItemRepository,
    badgeRepository: NotificationBadgeRepository,
    private val memoRepository: MemoRepository
) : ViewModel() {

    private val permissions = MutableStateFlow(readPermissions())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _memo = MutableStateFlow(MemoPanelState(date = LocalDate.now()))
    val memo: StateFlow<MemoPanelState> = _memo.asStateFlow()
    private var saveJob: Job? = null

    /** 주간 달력 = 고른 날짜가 속한 주 (‹ › 버튼은 고른 날짜를 7일씩 옮김) */
    private val week = selectedDate.flatMapLatest { selected ->
        val start = DateKey.weekStart(selected)
        combine(
            memoRepository.observeMemoDates(start, start.plusDays(6)),
            routineRepository.getRoutines()
        ) { memoDates, routines ->
            (0L..6L).map { offset ->
                val date = start.plusDays(offset)
                WeekDayCell(
                    date = date,
                    isToday = date == LocalDate.now(),
                    isSelected = date == selected,
                    hasMemo = date in memoDates,
                    routineCount = routines.count { it.isEnabled && RoutineSchedule.isScheduledOn(it.daysOfWeek, date) }
                )
            }
        }
    }

    private val pinned = chatHeadItemRepository.observeItems()
        .map { rows -> ChatHeadOrdering.build(emptyList(), rows.map { it.toPinned() }) }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(todayRepository.observeToday(), selectedDate.flatMapLatest { todayRepository.observeDay(it) }) { t, d -> t to d },
        settingsRepository.observeSettings(),
        pinned,
        combine(badgeRepository.observeBadges(), week) { b, w -> b to w },
        permissions
    ) { (today, day), settings, entries, (badges, weekCells), perms ->
        val offset = weekCells.firstOrNull()?.let {
            java.time.temporal.ChronoUnit.WEEKS.between(DateKey.weekStart(LocalDate.now()), it.date)
        } ?: 0L
        HomeUiState(today, day, settings, entries, badges, weekCells, offset, perms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // ---------------------------------------------------------------- 요일 선택 + 메모

    init {
        // 메모지에 보이는 날짜의 메모를 계속 지켜봄: 전체 메모 화면에서 고친 내용도 바로 반영
        // (입력 중이라 아직 저장 안 된 글이 있으면 덮어쓰지 않음)
        viewModelScope.launch {
            _memo.map { it.date }.distinctUntilChanged()
                .flatMapLatest { date -> memoRepository.observeMemo(date).map { date to it } }
                .collect { (date, text) ->
                    _memo.update { m ->
                        if (m.date == date && m.loaded && m.saved && m.text != text) m.copy(text = text) else m
                    }
                }
        }
    }

    /** 요일을 누르면: 그 날짜의 루틴을 보여주고 메모지를 엶. 열린 날짜를 다시 누르면 메모지만 닫힘 */
    fun selectDate(date: LocalDate) {
        val current = _memo.value
        if (date == current.date && current.open) {
            closeMemo()
            return
        }
        openDate(date, openMemo = true)
    }

    /** ‹ › : 같은 요일로 한 주 앞/뒤. 메모지가 열려 있으면 그 날짜의 메모로 바뀜 */
    fun shiftWeek(delta: Long) {
        openDate(selectedDate.value.plusWeeks(delta), openMemo = _memo.value.open)
    }

    /** 메모지를 닫은 자리의 "메모 작성" 버튼: 고른 날짜의 메모지를 다시 엶 */
    fun openMemo() = openDate(selectedDate.value, openMemo = true)

    /** 전체 메모 화면에서 "홈에서 보기"를 눌렀을 때 */
    fun showMemo(date: LocalDate) = openDate(date, openMemo = true)

    private fun openDate(date: LocalDate, openMemo: Boolean) {
        selectedDate.value = date
        saveJob?.cancel()
        viewModelScope.launch {
            saveMemoNow()
            val text = memoRepository.getMemo(date)
            _memo.value = MemoPanelState(date = date, open = openMemo, text = text, loaded = true)
        }
    }

    fun closeMemo() {
        flushMemo()
        _memo.update { it.copy(open = false) }
    }

    /** 다시 오늘(이번 주) 보기. 메모지가 열려 있으면 오늘 메모로 */
    fun backToToday() = openDate(LocalDate.now(), openMemo = _memo.value.open)

    /** 입력할 때마다 0.6초 뒤 자동 저장 */
    fun onMemoChange(text: String) {
        _memo.update { it.copy(text = text, saved = false) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            saveMemoNow()
        }
    }

    fun flushMemo() {
        saveJob?.cancel()
        viewModelScope.launch { saveMemoNow() }
    }

    private suspend fun saveMemoNow() = withContext(NonCancellable) {
        val s = _memo.value
        if (!s.loaded || s.saved) return@withContext
        memoRepository.saveMemo(s.date, s.text)
        _memo.update { if (it.date == s.date && it.text == s.text) it.copy(saved = true) else it }
    }

    override fun onCleared() {
        // 화면이 사라질 때 마지막 입력도 저장 (viewModelScope는 이미 취소된 상태)
        val s = _memo.value
        if (s.loaded && !s.saved) runBlocking { memoRepository.saveMemo(s.date, s.text) }
    }

    // ---------------------------------------------------------------- 그 외

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

    /** 고른 날짜의 체크 (앞으로의 날짜는 화면에서 막음) */
    fun toggleCheck(routineId: Long, packageName: String, checked: Boolean) {
        val date = selectedDate.value
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            routineRepository.setChecked(routineId, packageName, DateKey.of(date), checked)
        }
    }

    private fun readPermissions() = PermissionWarnings(
        overlayMissing = !PermissionUtils.hasOverlayPermission(context),
        notificationMissing = !PermissionUtils.hasNotificationPermission(context),
        exactAlarmMissing = !PermissionUtils.canScheduleExactAlarms(context),
        listenerMissing = !PermissionUtils.hasNotificationListenerAccess(context)
    )
}
