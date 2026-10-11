package com.routinecheck.app.ui.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.data.repository.RoutineWithApps
import com.routinecheck.app.ui.common.AppIcon
import com.routinecheck.app.util.DayMask
import com.routinecheck.app.util.TimeText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

/** 루틴 탭: 전체 루틴 목록 + 켜기/끄기 + 새 루틴 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onOpenRoutine: (Long) -> Unit,
    onNewRoutine: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("루틴") }) },
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
                    Text("아직 루틴이 없어요", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "예) 기상 루틴: 7시 · 캘린더, 날씨, 메신저 확인",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.routine.routineId }) { item ->
                    RoutineCard(
                        item = item,
                        onClick = { onOpenRoutine(item.routine.routineId) },
                        onEnabledChange = { viewModel.setEnabled(item.routine.routineId, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(item: RoutineWithApps, onClick: () -> Unit, onEnabledChange: (Boolean) -> Unit) {
    val routine = item.routine
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp).alpha(if (routine.isEnabled) 1f else 0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(TimeText.display(routine.startTime), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(routine.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append(DayMask.label(routine.daysOfWeek))
                            if (routine.autoStart) append(" · 챗헤드 자동 펼침")
                            if (routine.alarmLinked) append(" · 시계 알람 연동")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = routine.isEnabled, onCheckedChange = onEnabledChange)
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
