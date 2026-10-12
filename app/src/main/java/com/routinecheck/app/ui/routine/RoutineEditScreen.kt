package com.routinecheck.app.ui.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.routinecheck.app.domain.DayMask
import com.routinecheck.app.domain.TimeText
import com.routinecheck.app.system.PermissionUtils
import com.routinecheck.app.system.SystemAlarmLinker
import com.routinecheck.app.ui.common.AppIcon
import com.routinecheck.app.ui.common.AppPickerDialog
import com.routinecheck.app.ui.common.DateInput
import com.routinecheck.app.ui.common.SectionHeader
import com.routinecheck.app.ui.common.TimeInput

/**
 * ⑧ 루틴 만들기/수정: 이름, 메시지, 시각(직접 입력), 반복 요일, 등록 앱, 시작 알림(선택)·스누즈, 챗헤드 자동 펼침, 시계 알람 연동
 * v0.5.0 일정 만들기/수정: 요일 대신 날짜(직접 입력 또는 달력)를 받고, 나머지는 루틴과 같음 (시계 앱 연동만 없음)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditScreen(
    onBack: () -> Unit,
    viewModel: RoutineEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val kind = if (state.isSchedule) "일정" else "루틴"
    var showAppPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RoutineEditEvent.Saved -> {
                    event.linkAlarm?.let { routine ->
                        try {
                            context.startActivity(SystemAlarmLinker.setAlarmIntent(routine))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "알람을 만들 수 있는 시계 앱이 없어요", Toast.LENGTH_SHORT).show()
                        }
                    }
                    onBack()
                }
                RoutineEditEvent.Deleted -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "새 $kind" else "$kind 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "삭제") }
                    }
                    TextButton(onClick = viewModel::save, enabled = !state.isLoading) { Text("저장") }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("$kind 이름") },
                placeholder = { Text(if (state.isSchedule) "예) 치과 예약" else "예) 기상 루틴") },
                isError = state.nameError,
                supportingText = { if (state.nameError) Text("이름을 입력해 주세요") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // v0.5.0: 실행될 때 화면에 띄울 메시지
            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::setMessage,
                label = { Text("메시지 (선택)") },
                placeholder = { Text(if (state.isSchedule) "예) 보험증 챙기기, 10분 일찍 출발" else "예) 물 한 잔 마시고 시작하기") },
                supportingText = {
                    Text("$kind 시각에 사용 중인 화면 위로 이 메시지를 띄워요 · ${state.message.length}/$MESSAGE_MAX")
                },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.isSchedule) {
                SectionHeader("날짜")
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        DateInput(date = state.date, onChange = viewModel::setDate, modifier = Modifier.fillMaxWidth())
                        Text(
                            "숫자를 눌러 직접 입력하거나 달력 버튼으로 고르세요 · " +
                                "${state.date.year}년 ${state.date.monthValue}월 ${state.date.dayOfMonth}일 " +
                                "${DayMask.shortName(state.date.dayOfWeek)}요일",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            SectionHeader("시작 시각")
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    TimeInput(hour = state.hour, minute = state.minute, onChange = viewModel::setTime)
                    Text(
                        "숫자를 눌러 직접 입력하세요 · ${TimeText.display(TimeText.format(state.hour, state.minute))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            if (state.isPastSchedule) {
                Text(
                    "이미 지난 시각이에요. 저장은 되지만 알림과 메시지는 울리지 않아요.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (!state.isSchedule) {
                SectionHeader("반복 요일")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    DayMask.ORDERED.forEach { day ->
                        FilterChip(
                            selected = DayMask.contains(state.daysOfWeek, day),
                            onClick = { viewModel.toggleDay(day) },
                            label = { Text(DayMask.shortName(day)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { viewModel.setDays(DayMask.ALL) }, label = { Text("매일") })
                    AssistChip(onClick = { viewModel.setDays(DayMask.WEEKDAYS) }, label = { Text("평일") })
                    AssistChip(onClick = { viewModel.setDays(DayMask.WEEKEND) }, label = { Text("주말") })
                }
                if (state.daysError) {
                    Text("요일을 하나 이상 골라 주세요", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionHeader("확인할 앱") {
                TextButton(onClick = { showAppPicker = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("앱 추가")
                }
            }
            if (state.apps.isEmpty()) {
                Text(
                    "$kind 시간에 챗헤드 맨 앞에 올라올 앱을 골라 주세요. 챗헤드에서 열면 자동으로 체크돼요." +
                        if (state.isSchedule) " 앱 없이 알림·메시지만 받아도 돼요." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.apps.forEachIndexed { index, app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    AppIcon(app.packageName, app.label, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.moveApp(app.packageName, -1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "위로")
                    }
                    IconButton(onClick = { viewModel.moveApp(app.packageName, 1) }, enabled = index < state.apps.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "아래로")
                    }
                    IconButton(onClick = { viewModel.removeApp(app.packageName) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "빼기")
                    }
                }
            }

            SectionHeader("알림")
            ToggleRow("시작 알림", "$kind 시각에 알림을 보내요. 소리·진동 방식은 설정에서 바꿀 수 있어요", state.notifyEnabled, viewModel::setNotifyEnabled)
            if (state.notifyEnabled && !PermissionUtils.hasNotificationPermission(context)) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                }) { Text("알림 권한이 꺼져 있어요 — 허용하러 가기") }
            }
            if (state.notifyEnabled) {
                Text("다시 알림 (스누즈)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, 5, 10, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = state.snoozeMinutes == minutes,
                            onClick = { viewModel.setSnooze(minutes) },
                            label = { Text(if (minutes == null) "사용 안 함" else "${minutes}분") }
                        )
                    }
                }
            }

            SectionHeader("동작")
            ToggleRow("챗헤드 자동 펼치기", "$kind 시각이 되면 챗헤드가 펼쳐지며 $kind 앱을 보여줘요", state.autoStart, viewModel::setAutoStart)
            if (!state.isSchedule) {
                // 시계 앱 알람 인텐트는 요일 반복만 받을 수 있어서 날짜 일정에는 표시하지 않음
                ToggleRow("시계 앱 알람과 연동", "저장할 때 시계 앱에도 같은 시각의 알람을 만들어요", state.alarmLinked, viewModel::setAlarmLinked)
            }
            ToggleRow("$kind 사용", "끄면 알림과 챗헤드 우선 표시가 멈춰요", state.isEnabled, viewModel::setEnabled)
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            title = "$kind 앱 선택",
            initialSelected = state.apps,
            onDismiss = { showAppPicker = false },
            onConfirm = {
                viewModel.setApps(it)
                showAppPicker = false
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (state.isSchedule) "일정을 삭제할까요?" else "루틴을 삭제할까요?") },
            text = {
                Text(
                    if (state.isSchedule) "체크 기록과 알람도 함께 삭제돼요."
                    else "체크 기록과 알람도 함께 삭제돼요. 시계 앱에 만든 알람은 시계 앱에서 지워 주세요."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
