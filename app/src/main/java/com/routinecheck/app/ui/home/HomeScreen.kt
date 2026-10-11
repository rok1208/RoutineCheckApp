package com.routinecheck.app.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routinecheck.app.data.repository.TodayRoutine
import com.routinecheck.app.onboarding.OnboardingActivity
import com.routinecheck.app.domain.ChatHeadKind
import com.routinecheck.app.ui.common.AppIcon
import com.routinecheck.app.ui.common.ChatHeadItemIcon
import com.routinecheck.app.ui.common.BadgeDot
import com.routinecheck.app.ui.common.ChatHeadPreview
import com.routinecheck.app.ui.common.SectionHeader
import com.routinecheck.app.ui.common.StatusChip
import com.routinecheck.app.ui.theme.Brand
import com.routinecheck.app.util.DateKey
import com.routinecheck.app.util.DayMask
import com.routinecheck.app.util.TimeText
import java.time.LocalDate

/**
 * ① 홈. 챗헤드가 앱의 중심이므로 맨 위에 챗헤드 카드(켜기/끄기, 등록 항목)를 두고,
 * 그 아래에 주간 달력 → (요일을 누르면) 그 날의 메모지 → 그 날의 루틴(직접 체크 가능)을 배치합니다.
 */
@Composable
fun HomeScreen(
    onOpenRoutine: (Long) -> Unit,
    onNewRoutine: () -> Unit,
    onManageChatHeadApps: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val memo by viewModel.memo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flushMemo() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(todayTitle(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("루틴 체크", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "설정") }
            }
        }

        if (state.permissions.requiredMissing) {
            item {
                PermissionWarningCard(onFix = {
                    context.startActivity(Intent(context, OnboardingActivity::class.java).putExtra(OnboardingActivity.EXTRA_FROM_APP, true))
                })
            }
        }

        item {
            ChatHeadCard(
                state = state,
                onToggle = viewModel::setChatHeadEnabled,
                onManageApps = onManageChatHeadApps,
                onOpenSettings = onOpenSettings
            )
        }

        item {
            SectionHeader("이번 주")
            WeekStrip(cells = state.week, onClick = viewModel::selectDate)
        }

        // 요일을 누르면 달력과 루틴 사이에 그 날의 메모지가 펼쳐짐
        if (memo.open) {
            item(key = "memo") {
                MemoPaper(
                    memo = memo,
                    onChange = viewModel::onMemoChange,
                    onClose = viewModel::closeMemo
                )
            }
        }

        val day = state.day
        item {
            SectionHeader(dayTitle(day)) {
                if (day != null && !day.isToday) {
                    TextButton(onClick = viewModel::backToToday) { Text("오늘로") }
                } else {
                    TextButton(onClick = onNewRoutine) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("루틴 만들기")
                    }
                }
            }
        }

        val routines = day?.routines.orEmpty()
        if (day != null && routines.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(if (day.isToday) "오늘 예정된 루틴이 없어요" else "이 날 예정된 루틴이 없어요", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "루틴을 만들면 시간에 맞춰 알려주고, 루틴 앱이 챗헤드 맨 앞에 올라와요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(routines, key = { it.routine.routineId }) { today ->
            TodayRoutineCard(
                today = today,
                isPast = day?.isPast == true,
                canCheck = day?.isFuture != true,
                onOpen = { onOpenRoutine(today.routine.routineId) },
                onToggle = { pkg, checked -> viewModel.toggleCheck(today.routine.routineId, pkg, checked) }
            )
            Spacer(Modifier.height(10.dp))
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** "오늘의 루틴" / "10월 13일 (화) 루틴" */
private fun dayTitle(day: com.routinecheck.app.data.repository.TodaySnapshot?): String {
    if (day == null || day.isToday) return "오늘의 루틴"
    val d = day.date
    return "${d.monthValue}월 ${d.dayOfMonth}일 (${DayMask.shortName(d.dayOfWeek)}) 루틴"
}

/** 홈 안의 메모지: 바로 입력, 0.6초 뒤 자동 저장 */
@Composable
private fun MemoPaper(memo: MemoPanelState, onChange: (String) -> Unit, onClose: () -> Unit) {
    val paper = Color(0xFFFFF4C9)
    val ink = Color(0xFF3B3320)
    Card(
        colors = CardDefaults.cardColors(containerColor = paper),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, tint = ink)
                Spacer(Modifier.width(6.dp))
                val d = memo.date
                Text(
                    "${d.monthValue}월 ${d.dayOfMonth}일 (${DayMask.shortName(d.dayOfWeek)}) 메모",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    when {
                        !memo.loaded -> ""
                        memo.saved -> "저장됨"
                        else -> "저장 중…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.6f)
                )
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "메모 닫기", tint = ink) }
            }
            BasicTextField(
                value = memo.text,
                onValueChange = onChange,
                enabled = memo.loaded,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
                cursorBrush = SolidColor(ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .heightIn(min = 120.dp),
                decorationBox = { inner ->
                    Box {
                        if (memo.text.isEmpty()) {
                            Text(
                                "이 날의 할 일이나 생각을 적어 보세요",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ink.copy(alpha = 0.4f)
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

private fun todayTitle(): String {
    val d = LocalDate.now()
    return "${d.monthValue}월 ${d.dayOfMonth}일 ${DayMask.shortName(d.dayOfWeek)}요일"
}

@Composable
private fun PermissionWarningCard(onFix: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "'다른 앱 위에 표시' 권한이 꺼져 있어 챗헤드가 동작하지 않아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onFix) { Text("해결하기") }
        }
    }
}

@Composable
private fun ChatHeadCard(
    state: HomeUiState,
    onToggle: (Boolean) -> Unit,
    onManageApps: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val settings = state.settings
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Brand.Mint, Brand.Blue)))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(60.dp).background(Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { ChatHeadPreview(settings.iconUri, 50.dp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("챗헤드", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (settings.enabled) "켜짐 · ${settings.uiStyle.label}" else "꺼짐",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.enabled && !state.permissions.overlayMissing,
                        onCheckedChange = onToggle,
                        enabled = !state.permissions.overlayMissing,
                        colors = SwitchDefaults.colors(checkedTrackColor = Color.White.copy(alpha = 0.5f), checkedThumbColor = Color.White)
                    )
                }

                val activeCount = state.today?.active?.size ?: 0
                if (activeCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "진행 중인 루틴 ${activeCount}개의 앱이 챗헤드 맨 앞에 올라와 있어요",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(14.dp))
                if (state.pinnedEntries.isEmpty()) {
                    Text(
                        "자주 쓰는 앱·웹사이트를 등록하거나 폴더로 묶으면 어느 화면에서든 챗헤드로 바로 열 수 있어요.",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.pinnedEntries, key = { it.key }) { entry ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                                Box {
                                    Box(
                                        Modifier.size(48.dp).background(Color.White, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { ChatHeadItemIcon(entry.kind, entry.packageName, entry.label, 36.dp) }
                                    val badge = when (entry.kind) {
                                        ChatHeadKind.APP -> entry.packageName?.let { state.badges[it] }
                                        ChatHeadKind.FOLDER -> entry.folderPackages.mapNotNull { state.badges[it] }
                                            .takeIf { it.isNotEmpty() }
                                            ?.let { list -> com.routinecheck.app.data.repository.Badge(list.sumOf { it.count }, list.any { it.urgent }) }
                                        ChatHeadKind.LINK -> null
                                    }
                                    badge?.let { BadgeDot(it, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)) }
                                }
                                Text(
                                    entry.label,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onManageApps) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("항목 관리")
                    }
                    FilledTonalButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("챗헤드 설정")
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(cells: List<WeekDayCell>, onClick: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEach { cell ->
            val selectedColor = MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (cell.isSelected) selectedColor else MaterialTheme.colorScheme.surfaceVariant)
                    // 오늘이지만 다른 날을 보고 있으면 테두리로만 표시
                    .then(
                        if (cell.isToday && !cell.isSelected) Modifier.border(BorderStroke(2.dp, selectedColor), RoundedCornerShape(14.dp))
                        else Modifier
                    )
                    .clickable { onClick(cell.date) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val textColor = if (cell.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(DayMask.shortName(cell.date.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = textColor)
                Text("${cell.date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (cell.routineCount > 0) Dot(if (cell.isSelected) Color.White else Brand.Blue)
                    if (cell.hasMemo) Dot(Color(0xFFF5B83D))
                    if (cell.routineCount == 0 && !cell.hasMemo) Spacer(Modifier.size(6.dp))
                }
            }
        }
    }
    Text(
        "● 파랑: 루틴 있음  ● 노랑: 메모 있음 · 날짜를 누르면 그 날의 메모와 루틴을 볼 수 있어요",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
}

@Composable
private fun TodayRoutineCard(
    today: TodayRoutine,
    isPast: Boolean,
    canCheck: Boolean,
    onOpen: () -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    val routine = today.routine
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${TimeText.display(routine.startTime)} · ${DayMask.label(routine.daysOfWeek)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    today.isComplete -> StatusChip("완료", Brand.Green.copy(alpha = 0.18f), Brand.Green)
                    isPast -> StatusChip("미완료", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    today.hasStarted -> StatusChip("진행 중", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    else -> StatusChip("예정", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (today.apps.isEmpty()) {
                Text(
                    "등록된 앱이 없어요. 눌러서 앱을 추가하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                return@Column
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (today.total == 0) 0f else today.done.toFloat() / today.total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            Text(
                "${today.done}/${today.total} 확인",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            today.apps.forEach { app ->
                val checked = app.packageName in today.checkedPackages
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = canCheck) { onToggle(app.packageName, !checked) }
                        .padding(vertical = 4.dp)
                ) {
                    AppIcon(app.packageName, app.appLabel, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        app.appLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(checked = checked, onCheckedChange = { onToggle(app.packageName, it) }, enabled = canCheck)
                }
            }
        }
    }
}
