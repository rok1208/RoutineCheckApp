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
import android.content.Intent
import android.provider.Settings
import com.routinecheck.app.domain.DayMask
import com.routinecheck.app.domain.TimeText
import com.routinecheck.app.system.PermissionUtils
import com.routinecheck.app.ui.common.AppIcon
import com.routinecheck.app.ui.common.AppPermission
import com.routinecheck.app.ui.common.CoachStep
import com.routinecheck.app.ui.common.CoachTour
import com.routinecheck.app.ui.common.coachTarget
import com.routinecheck.app.ui.common.rememberPermissionRequester
import com.routinecheck.app.ui.common.AppPickerDialog
import com.routinecheck.app.ui.common.DateInput
import com.routinecheck.app.ui.common.SectionHeader
import com.routinecheck.app.ui.common.TimeInput

/**
 * ⑧ 루틴 만들기/수정: 이름, 메시지, 시각(직접 입력), 반복 요일, 등록 앱, 시작 알림(선택)·스누즈, 챗헤드 자동 펼침
 * v0.5.0 일정 만들기/수정: 요일 대신 날짜(직접 입력 또는 달력)를 받고, 나머지는 루틴과 같음
 * v0.5.1 시계 앱 알람 연동 삭제 (앱 자체 알림의 소리·진동으로 충분)
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
    val permissionRequester = rememberPermissionRequester()

    // 저장: 입력 확인 → 이 루틴에 필요한 권한만 그때 물어봄 → 허용 여부와 상관없이 저장
    val onSave: () -> Unit = {
        if (viewModel.validate()) {
            val willFire = state.isEnabled && !state.isPastSchedule
            val needs = buildList {
                if (willFire && state.notifyEnabled) add(AppPermission.NOTIFICATION)
                if (willFire && (state.notifyEnabled || state.autoStart)) add(AppPermission.EXACT_ALARM)
            }
            permissionRequester.request(
                needs,
                reasons = mapOf(
                    AppPermission.NOTIFICATION to "$kind 시각에 알림을 보내려면 알림 권한이 필요해요. 허용하지 않아도 ${kind}은 저장돼요.",
                    AppPermission.EXACT_ALARM to "허용하면 $kind 시각에 정확히 알려드려요. 허용하지 않으면 몇 분 늦게 울릴 수 있어요."
                )
            ) { viewModel.save() }
        }
    }

    CoachTour(
        id = if (state.isSchedule) "schedule_edit" else "routine_edit",
        enabled = !state.isLoading,
        steps = editTourSteps(kind = kind, isSchedule = state.isSchedule)
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                RoutineEditEvent.Saved -> onBack()
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
                    TextButton(onClick = onSave, enabled = !state.isLoading, modifier = Modifier.coachTarget("edit.save")) { Text("저장") }
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
                modifier = Modifier.fillMaxWidth().coachTarget("edit.message")
            )

            if (state.isSchedule) {
                SectionHeader("날짜")
                OutlinedCard(modifier = Modifier.fillMaxWidth().coachTarget("edit.when")) {
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
            OutlinedCard(modifier = Modifier.fillMaxWidth().coachTarget("edit.time")) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().coachTarget("edit.when")) {
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

            SectionHeader("확인할 앱", modifier = Modifier.coachTarget("edit.apps")) {
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

            SectionHeader("알림", modifier = Modifier.coachTarget("edit.notify"))
            ToggleRow("시작 알림", "$kind 시각에 알림을 보내요. 소리·진동 방식은 설정에서 바꿀 수 있어요", state.notifyEnabled, viewModel::setNotifyEnabled)
            if (state.notifyEnabled && !PermissionUtils.hasNotificationPermission(context)) {
                TextButton(onClick = {
                    permissionRequester.request(listOf(AppPermission.NOTIFICATION), userInitiated = true)
                }) { Text("알림 권한이 꺼져 있어요 — 허용하기") }
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
                Text("체크 기록과 알람도 함께 삭제돼요.")
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

/** 루틴/일정 만들기 화면 코치마크 가이드 (처음 열 때). 화면 위에서 아래 순서로 설명 */
private fun editTourSteps(kind: String, isSchedule: Boolean): List<CoachStep> {
    val message = CoachStep(
        target = "edit.message",
        title = "메시지",
        body = "$kind 시각이 되면 쓰고 있던 화면 위로 이 메시지를 띄워요. 비워 둬도 괜찮아요."
    )
    val time = CoachStep(
        target = "edit.time",
        title = "시작 시각",
        body = "시·분 숫자를 눌러 바로 입력해요. 이 시각에 알림이 오고, 등록한 앱이 챗헤드 맨 앞에 올라와요."
    )
    val whenStep = if (isSchedule) {
        CoachStep(
            target = "edit.when",
            title = "날짜",
            body = "숫자를 눌러 년·월·일을 바로 입력하거나, 오른쪽 달력 버튼으로 골라요. 일정은 이 날짜에 한 번만 실행돼요."
        )
    } else {
        CoachStep(
            target = "edit.when",
            title = "반복 요일",
            body = "루틴을 반복할 요일을 골라요. 아래 '매일 · 평일 · 주말'로 한 번에 고를 수도 있어요."
        )
    }
    val apps = CoachStep(
        target = "edit.apps",
        title = "확인할 앱",
        body = "$kind 시간에 이 앱들이 챗헤드 맨 앞에 올라와요. 챗헤드에서 열면 자동으로 체크되고, 모두 열면 완료예요."
    )
    val notify = CoachStep(
        target = "edit.notify",
        title = "알림",
        body = "시작 알림과 다시 알림(스누즈)을 정해요. 알림 권한이 필요하면 저장할 때 물어볼게요."
    )
    val save = CoachStep(
        target = "edit.save",
        title = "저장",
        body = "다 정했으면 저장을 눌러요. 저장한 ${kind}은 홈과 루틴 탭에서 볼 수 있어요."
    )
    // 일정: 메시지 → 날짜 → 시각 / 루틴: 메시지 → 시각 → 요일 (화면 배치 순서)
    return if (isSchedule) listOf(message, whenStep, time, apps, notify, save)
    else listOf(message, time, whenStep, apps, notify, save)
}
