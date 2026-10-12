package com.routinecheck.app.ui.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.local.isSchedule
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.data.repository.RoutineWithApps
import com.routinecheck.app.domain.DateKey
import com.routinecheck.app.domain.DayMask
import com.routinecheck.app.domain.TimeText
import com.routinecheck.app.ui.common.AppIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineRepository: RoutineRepository
) : ViewModel() {
    val routines: StateFlow<List<RoutineWithApps>?> = routineRepository.observeRoutinesWithApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(routineId: Long, enabled: Boolean) {
        viewModelScope.launch { routineRepository.setEnabled(routineId, enabled) }
    }
}

/** 루틴 탭: 일정(날짜 지정) + 반복 루틴 목록, 켜기/끄기, 새 루틴 · 새 일정 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onOpenRoutine: (Long) -> Unit,
    onNewRoutine: () -> Unit,
    onNewSchedule: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("루틴") },
                actions = {
                    TextButton(onClick = onNewSchedule) {
                        Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("일정 추가")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewRoutine,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("새 루틴") }
            )
        }
    ) { padding ->
        val list = routines
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 루틴과 일정이 없어요", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "예) 기상 루틴: 7시 · 캘린더, 날씨, 메신저 확인",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                // 일정: 다가오는 것부터 날짜·시각 순, 지난 일정은 맨 아래 / 반복 루틴: 시각 순
                val now = LocalDateTime.now()
                val (schedules, repeating) = list.partition { it.routine.isSchedule }
                val sortedSchedules = schedules
                    .sortedBy { "${it.routine.date} ${it.routine.startTime}" }
                    .sortedBy { scheduleAt(it)?.isBefore(now) ?: true }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sortedSchedules.isNotEmpty()) {
                        item(key = "label-schedules") { GroupLabel("일정") }
                        items(sortedSchedules, key = { it.routine.routineId }) { item ->
                            RoutineCard(
                                item = item,
                                isPast = scheduleAt(item)?.isBefore(now) ?: false,
                                onClick = { onOpenRoutine(item.routine.routineId) },
                                onEnabledChange = { viewModel.setEnabled(item.routine.routineId, it) }
                            )
                        }
                    }
                    if (repeating.isNotEmpty()) {
                        if (sortedSchedules.isNotEmpty()) item(key = "label-routines") { GroupLabel("반복 루틴") }
                        items(repeating, key = { it.routine.routineId }) { item ->
                            RoutineCard(
                                item = item,
                                isPast = false,
                                onClick = { onOpenRoutine(item.routine.routineId) },
                                onEnabledChange = { viewModel.setEnabled(item.routine.routineId, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 일정의 날짜·시각 (일정이 아니거나 날짜가 잘못되면 null) */
private fun scheduleAt(item: RoutineWithApps): LocalDateTime? {
    val date = item.routine.date ?: return null
    return runCatching { DateKey.parse(date).atTime(TimeText.parse(item.routine.startTime)) }.getOrNull()
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun RoutineCard(item: RoutineWithApps, isPast: Boolean, onClick: () -> Unit, onEnabledChange: (Boolean) -> Unit) {
    val routine = item.routine
    val isSchedule = routine.isSchedule
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (isSchedule) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp).alpha(if (routine.isEnabled && !isPast) 1f else 0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(TimeText.display(routine.startTime), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(routine.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            if (isSchedule) {
                                append("📅 ").append(scheduleDateLabel(routine.date))
                                if (isPast) append(" · 지난 일정")
                            } else {
                                append(DayMask.label(routine.daysOfWeek))
                            }
                            append(if (routine.notifyEnabled) " · 🔔 알림" else " · 🔕 알림 끔")
                            if (routine.autoStart) append(" · 챗헤드 자동 펼침")
                            if (routine.alarmLinked) append(" · 시계 알람 연동")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = routine.isEnabled, onCheckedChange = onEnabledChange)
            }
            routine.message?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(
                    "💬 $msg",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (item.apps.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item.apps.take(6).forEach { AppIcon(it.packageName, it.appLabel, 28.dp) }
                    if (item.apps.size > 6) {
                        Text("+${item.apps.size - 6}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** "2026년 10월 15일 (목)" */
private fun scheduleDateLabel(date: String?): String {
    val d = date?.let { runCatching { DateKey.parse(it) }.getOrNull() } ?: return date.orEmpty()
    return "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일 (${DayMask.shortName(d.dayOfWeek)})"
}
