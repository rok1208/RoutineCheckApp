package com.routinecheck.app.ui.routineedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.local.entity.RoutineAppEntity
import com.routinecheck.app.data.local.entity.RoutineEntity
import com.routinecheck.app.data.repository.InstalledAppInfo
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.util.DayMask
import com.routinecheck.app.util.TimeText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class RoutineEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val daysOfWeek: Int = DayMask.WEEKDAYS,
    val autoStart: Boolean = true,
    val alarmLinked: Boolean = false,
    /** null이면 스누즈 없음 */
    val snoozeMinutes: Int? = 10,
    val isEnabled: Boolean = true,
    val apps: List<InstalledAppInfo> = emptyList(),
    val nameError: Boolean = false,
    val daysError: Boolean = false
)

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
    private var original: RoutineEntity? = null

    private val _state = MutableStateFlow(RoutineEditUiState())
    val state: StateFlow<RoutineEditUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RoutineEditEvent>()
    val events: SharedFlow<RoutineEditEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val existing = if (routineId > 0) routineRepository.getRoutineWithApps(routineId) else null
            if (existing == null) {
                _state.update { it.copy(isLoading = false, isNew = true) }
            } else {
                original = existing.routine
                val r = existing.routine
                val time = TimeText.parse(r.startTime)
                _state.value = RoutineEditUiState(
                    isLoading = false,
                    isNew = false,
                    name = r.name,
                    hour = time.hour,
                    minute = time.minute,
                    daysOfWeek = r.daysOfWeek,
                    autoStart = r.autoStart,
                    alarmLinked = r.alarmLinked,
                    snoozeMinutes = r.snoozeMinutes,
                    isEnabled = r.isEnabled,
                    apps = existing.apps.map { InstalledAppInfo(it.packageName, it.appLabel) }
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value.take(30), nameError = false) }
    fun setTime(hour: Int, minute: Int) = _state.update { it.copy(hour = hour, minute = minute) }
    fun toggleDay(day: DayOfWeek) = _state.update { it.copy(daysOfWeek = DayMask.toggle(it.daysOfWeek, day), daysError = false) }
    fun setDays(mask: Int) = _state.update { it.copy(daysOfWeek = mask, daysError = false) }
    fun setAutoStart(value: Boolean) = _state.update { it.copy(autoStart = value) }
    fun setAlarmLinked(value: Boolean) = _state.update { it.copy(alarmLinked = value) }
    fun setSnooze(minutes: Int?) = _state.update { it.copy(snoozeMinutes = minutes) }
    fun setEnabled(value: Boolean) = _state.update { it.copy(isEnabled = value) }
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
        val daysError = s.daysOfWeek and DayMask.ALL == 0
        if (nameError || daysError) {
            _state.update { it.copy(nameError = nameError, daysError = daysError) }
            return
        }
        viewModelScope.launch {
            val base = original
            val routine = (base ?: RoutineEntity(name = "", startTime = "", daysOfWeek = 0)).copy(
                name = s.name.trim(),
                startTime = TimeText.format(s.hour, s.minute),
                daysOfWeek = s.daysOfWeek,
                autoStart = s.autoStart,
                alarmLinked = s.alarmLinked,
                snoozeMinutes = s.snoozeMinutes,
                isEnabled = s.isEnabled
            )
            val apps = s.apps.map { RoutineAppEntity(routineId = 0, packageName = it.packageName, appLabel = it.label) }
            val savedId = routineRepository.saveRoutine(routine, apps)

            // 시계 앱 알람: 새로 연동을 켰거나, 연동 중인데 시간/요일이 바뀐 경우에만 다시 만듦
            val needsLink = s.alarmLinked && (
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
