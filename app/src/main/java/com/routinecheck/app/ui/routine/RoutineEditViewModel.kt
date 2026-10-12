package com.routinecheck.app.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.local.RoutineAppEntity
import com.routinecheck.app.data.local.RoutineEntity
import com.routinecheck.app.data.repository.InstalledAppInfo
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.domain.DateKey
import com.routinecheck.app.domain.DayMask
import com.routinecheck.app.domain.TimeText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 루틴 / 일정 만들기·수정 화면 상태.
 * isSchedule = true 이면 "일정": 요일 대신 날짜(date)를 받고, 그 날짜·시각에 한 번만 실행됩니다.
 */
data class RoutineEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** v0.5.0: 날짜 지정 일정 (false = 요일 반복 루틴) */
    val isSchedule: Boolean = false,
    /** 일정 날짜 (isSchedule 일 때만 사용) */
    val date: LocalDate = LocalDate.now(),
    val name: String = "",
    /** v0.5.0: 실행될 때 화면에 띄울 메시지 (선택) */
    val message: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val daysOfWeek: Int = DayMask.WEEKDAYS,
    val autoStart: Boolean = true,
    val alarmLinked: Boolean = false,
    /** null이면 스누즈 없음 */
    val snoozeMinutes: Int? = 10,
    val isEnabled: Boolean = true,
    /** 시작 시각 알림 받기 (선택) */
    val notifyEnabled: Boolean = true,
    val apps: List<InstalledAppInfo> = emptyList(),
    val nameError: Boolean = false,
    val daysError: Boolean = false
) {
    /** 일정 시각이 이미 지났는지 (저장은 되지만 알림은 울리지 않음) */
    val isPastSchedule: Boolean
        get() = isSchedule && !date.atTime(hour, minute).isAfter(LocalDateTime.now())
}

sealed interface RoutineEditEvent {
    /** 저장 완료. linkAlarm이 있으면 화면에서 시계 앱 알람 인텐트를 실행 */
    data class Saved(val linkAlarm: RoutineEntity?) : RoutineEditEvent
    data object Deleted : RoutineEditEvent
}

@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val routineId: Long = savedStateHandle.get<Long>("routineId") ?: -1L
    /** "일정 추가"로 들어온 경우 그 날짜 (yyyy-MM-dd). 없으면 루틴 만들기 */
    private val scheduleDateArg: LocalDate? = savedStateHandle.get<String>("date")
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { DateKey.parse(it) }.getOrNull() }
    private var original: RoutineEntity? = null

    private val _state = MutableStateFlow(RoutineEditUiState())
    val state: StateFlow<RoutineEditUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RoutineEditEvent>()
    val events: SharedFlow<RoutineEditEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val existing = if (routineId > 0) routineRepository.getRoutineWithApps(routineId) else null
            if (existing == null) {
                _state.update {
                    if (scheduleDateArg != null) {
                        // 새 일정: 고른 날짜, 시각은 다음 정각 (오늘이면 지금보다 뒤)
                        val nextHour = (LocalDateTime.now().hour + 1).coerceAtMost(23)
                        it.copy(
                            isLoading = false, isNew = true, isSchedule = true, date = scheduleDateArg,
                            hour = if (scheduleDateArg == LocalDate.now()) nextHour else 9, minute = 0,
                            autoStart = false, alarmLinked = false, snoozeMinutes = 5
                        )
                    } else {
                        it.copy(isLoading = false, isNew = true)
                    }
                }
            } else {
                original = existing.routine
                val r = existing.routine
                val time = TimeText.parse(r.startTime)
                _state.value = RoutineEditUiState(
                    isLoading = false,
                    isNew = false,
                    isSchedule = r.date != null,
                    date = r.date?.let { d -> runCatching { DateKey.parse(d) }.getOrNull() } ?: LocalDate.now(),
                    name = r.name,
                    message = r.message.orEmpty(),
                    hour = time.hour,
                    minute = time.minute,
                    daysOfWeek = r.daysOfWeek,
                    autoStart = r.autoStart,
                    alarmLinked = r.alarmLinked,
                    snoozeMinutes = r.snoozeMinutes,
                    isEnabled = r.isEnabled,
                    notifyEnabled = r.notifyEnabled,
                    apps = existing.apps.map { InstalledAppInfo(it.packageName, it.appLabel) }
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value.take(30), nameError = false) }
    fun setMessage(value: String) = _state.update { it.copy(message = value.take(MESSAGE_MAX)) }
    fun setDate(value: LocalDate) = _state.update { it.copy(date = value) }
    fun setTime(hour: Int, minute: Int) = _state.update { it.copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59)) }
    fun toggleDay(day: DayOfWeek) = _state.update { it.copy(daysOfWeek = DayMask.toggle(it.daysOfWeek, day), daysError = false) }
    fun setDays(mask: Int) = _state.update { it.copy(daysOfWeek = mask, daysError = false) }
    fun setAutoStart(value: Boolean) = _state.update { it.copy(autoStart = value) }
    fun setAlarmLinked(value: Boolean) = _state.update { it.copy(alarmLinked = value) }
    fun setSnooze(minutes: Int?) = _state.update { it.copy(snoozeMinutes = minutes) }
    fun setEnabled(value: Boolean) = _state.update { it.copy(isEnabled = value) }
    fun setNotifyEnabled(value: Boolean) = _state.update { it.copy(notifyEnabled = value) }
    fun setApps(apps: List<InstalledAppInfo>) = _state.update { it.copy(apps = apps) }
    fun removeApp(packageName: String) = _state.update { s -> s.copy(apps = s.apps.filterNot { it.packageName == packageName }) }

    fun moveApp(packageName: String, delta: Int) = _state.update { s ->
        val list = s.apps.toMutableList()
        val from = list.indexOfFirst { it.packageName == packageName }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return@update s
        list.add(to, list.removeAt(from))
        s.copy(apps = list)
    }

    fun save() {
        val s = _state.value
        val nameError = s.name.isBlank()
        val daysError = !s.isSchedule && s.daysOfWeek and DayMask.ALL == 0
        if (nameError || daysError) {
            _state.update { it.copy(nameError = nameError, daysError = daysError) }
            return
        }
        viewModelScope.launch {
            val base = original
            val routine = (base ?: RoutineEntity(name = "", startTime = "", daysOfWeek = 0)).copy(
                name = s.name.trim(),
                startTime = TimeText.format(s.hour, s.minute),
                daysOfWeek = if (s.isSchedule) 0 else s.daysOfWeek,
                date = if (s.isSchedule) DateKey.of(s.date) else null,
                message = s.message.trim().ifBlank { null },
                autoStart = s.autoStart,
                // 시계 앱 알람은 요일 반복만 지원해서 일정에서는 쓰지 않음
                alarmLinked = s.alarmLinked && !s.isSchedule,
                snoozeMinutes = s.snoozeMinutes,
                isEnabled = s.isEnabled,
                notifyEnabled = s.notifyEnabled
            )
            val apps = s.apps.map { RoutineAppEntity(routineId = 0, packageName = it.packageName, appLabel = it.label) }
            val savedId = routineRepository.saveRoutine(routine, apps)

            // 시계 앱 알람: 새로 연동을 켰거나, 연동 중인데 시간/요일이 바뀐 경우에만 다시 만듦
            val needsLink = routine.alarmLinked && (
                base == null || !base.alarmLinked ||
                    base.startTime != routine.startTime || base.daysOfWeek != routine.daysOfWeek
                )
            _events.emit(RoutineEditEvent.Saved(if (needsLink) routine.copy(routineId = savedId) else null))
        }
    }

    fun delete() {
        if (routineId <= 0) return
        viewModelScope.launch {
            routineRepository.deleteRoutine(routineId)
            _events.emit(RoutineEditEvent.Deleted)
        }
    }
}

/** 루틴 메시지 최대 길이 */
const val MESSAGE_MAX = 200
