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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import com.routinecheck.app.ui.common.AppIcon
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
 * ① 홈. 챗헤드가 앱의 중심이므로 맨 위에 챗헤드 카드(켜기/끄기, 등록 앱)를 두고,
 * 그 아래에 보조 기능인 "오늘의 루틴"(직접 체크 가능)과 주간 메모 달력을 배치합니다.
 */
@Composable
fun HomeScreen(
    onOpenRoutine: (Long) -> Unit,
    onNewRoutine: () -> Unit,
    onOpenMemo: (String) -> Unit,
    onManageChatHeadApps: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
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
            WeekStrip(cells = state.week, onClick = { onOpenMemo(DateKey.of(it)) })
        }

        item {
            SectionHeader("오늘의 루틴") {
                TextButton(onClick = onNewRoutine) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("루틴 만들기")
                }
            }
        }

        val routines = state.today?.routines.orEmpty()
        if (state.today != null && routines.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("오늘 예정된 루틴이 없어요", style = MaterialTheme.typography.titleSmall)
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
                onOpen = { onOpenRoutine(today.routine.routineId) },
                onToggle = { pkg, checked -> viewModel.toggleCheck(today.routine.routineId, pkg, checked) }
            )
            Spacer(Modifier.height(10.dp))
        }
        item { Spacer(Modifier.height(24.dp)) }
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
                "필수 권한이 꺼져 있어 챗헤드나 알림이 동작하지 않을 수 있어요.",
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
                if (state.pinnedApps.isEmpty()) {
                    Text(
                        "자주 쓰는 앱을 등록하면 어느 화면에서든 챗헤드로 바로 열 수 있어요.",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.pinnedApps, key = { it.packageName }) { app ->
                            Box {
                                Box(
                                    Modifier.size(48.dp).background(Color.White, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) { AppIcon(app.packageName, app.appLabel, 36.dp) }
                                state.badges[app.packageName]?.let {
                                    BadgeDot(it, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onManageApps) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("앱 관리")
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
                    .background(if (cell.isToday) selectedColor else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onClick(cell.date) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val textColor = if (cell.isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(DayMask.shortName(cell.date.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = textColor)
                Text("${cell.date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (cell.routineCount > 0) Dot(if (cell.isToday) Color.White else Brand.Blue)
                    if (cell.hasMemo) Dot(Color(0xFFF5B83D))
                    if (cell.routineCount == 0 && !cell.hasMemo) Spacer(Modifier.size(6.dp))
                }
            }
        }
    }
    Text(
        "● 파랑: 루틴 있음  ● 노랑: 메모 있음 · 날짜를 누르면 메모를 쓸 수 있어요",
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
                        .clickable { onToggle(app.packageName, !checked) }
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
                    Checkbox(checked = checked, onCheckedChange = { onToggle(app.packageName, it) })
                }
            }
        }
    }
}
