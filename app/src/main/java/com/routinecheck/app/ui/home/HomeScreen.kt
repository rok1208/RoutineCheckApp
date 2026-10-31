package com.routinecheck.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routinecheck.app.data.local.isSchedule
import com.routinecheck.app.data.repository.TodayRoutine
import com.routinecheck.app.domain.ChatHeadKind
import com.routinecheck.app.domain.DateKey
import com.routinecheck.app.domain.DayMask
import com.routinecheck.app.domain.TimeText
import com.routinecheck.app.ui.Brand
import com.routinecheck.app.ui.common.AppIcon
import com.routinecheck.app.ui.common.BadgeDot
import com.routinecheck.app.ui.common.ChatHeadItemIcon
import com.routinecheck.app.ui.common.ChatHeadPreview
import com.routinecheck.app.ui.common.SectionHeader
import com.routinecheck.app.ui.common.StatusChip
import com.routinecheck.app.ui.common.CoachStep
import com.routinecheck.app.ui.common.CoachTour
import com.routinecheck.app.ui.common.coachTarget
import com.routinecheck.app.ui.common.enableChatHead
import com.routinecheck.app.ui.common.rememberPermissionRequester
import java.time.LocalDate

/**
 * ① 홈. 챗헤드가 앱의 중심이므로 맨 위에 챗헤드 카드(켜기/끄기, 등록 항목)를 두고,
 * 그 아래에 주간 달력 → (요일을 누르면) 그 날의 메모지 → 그 날의 루틴(직접 체크 가능)을 배치합니다.
 */
@Composable
fun HomeScreen(
    onOpenRoutine: (Long) -> Unit,
    onNewRoutine: () -> Unit,
    /** v0.5.0: 그 날짜 기준으로 새 일정 만들기 */
    onNewSchedule: (LocalDate) -> Unit,
    onManageChatHeadItems: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAllMemos: () -> Unit,
    /** 전체 메모 화면에서 "홈에서 보기"로 돌아왔을 때 열 날짜 */
    requestedMemoDate: LocalDate? = null,
    onRequestedMemoHandled: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val memo by viewModel.memo.collectAsStateWithLifecycle()
    val permissionRequester = rememberPermissionRequester()
    val listState = rememberLazyListState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    // 챗헤드 켜기는 "다른 앱 위에 표시" 권한을 확인한 뒤에만 (v0.6.0 권한은 쓰려는 순간에 요청)
    val toggleChatHead: (Boolean) -> Unit = { on ->
        if (on) permissionRequester.enableChatHead { viewModel.setChatHeadEnabled(true) }
        else viewModel.setChatHeadEnabled(false)
    }
    // 위에서부터 셌을 때 "오늘의 루틴" 머리말의 위치 (가이드에서 스크롤할 때 사용)
    val showPermissionCard = state.permissions.requiredMissing && state.settings.enabled
    val dayHeaderIndex = 5 + if (showPermissionCard) 1 else 0

    CoachTour(id = "home", steps = homeTourSteps(
        scrollTop = { listState.animateScrollToItem(0) },
        scrollToDayHeader = { listState.animateScrollToItem(dayHeaderIndex - 1) }
    ))
    LaunchedEffect(requestedMemoDate) {
        if (requestedMemoDate != null) {
            viewModel.showMemo(requestedMemoDate)
            onRequestedMemoHandled()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flushMemo() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(todayTitle(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("루틴 체크", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.coachTarget("home.settings")) {
                    Icon(Icons.Rounded.Settings, contentDescription = "설정")
                }
            }
        }

        // 챗헤드를 켜 두었는데 권한이 빠진 경우(설정에서 직접 끈 경우 등)에만 경고
        if (showPermissionCard) {
            item {
                PermissionWarningCard(onFix = { toggleChatHead(true) })
            }
        }

        item {
            ChatHeadCard(
                state = state,
                onToggle = toggleChatHead,
                onManageApps = onManageChatHeadItems,
                onOpenSettings = onOpenSettings
            )
        }

        item {
            WeekHeader(
                cells = state.week,
                offset = state.weekOffset,
                onPrev = { viewModel.shiftWeek(-1) },
                onNext = { viewModel.shiftWeek(1) },
                onThisWeek = viewModel::backToToday
            )
            // WeekStrip 은 날짜 줄 + 설명 문구 두 덩어리라 위아래로 쌓이는 Column 으로 감쌈 (Box 면 겹침)
            Column(Modifier.coachTarget("home.week")) {
                WeekStrip(cells = state.week, onClick = viewModel::selectDate)
            }
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
        } else {
            // 메모지를 닫으면 그 자리에 "메모 작성" 버튼 (메모가 있는 날이면 "메모 보기")
            val selected = state.week.firstOrNull { it.isSelected }
            item(key = "memo-open") {
                Box(Modifier.coachTarget("home.memo")) {
                    MemoOpenButton(
                        date = selected?.date ?: memo.date,
                        hasMemo = selected?.hasMemo == true,
                        onClick = viewModel::openMemo
                    )
                }
            }
        }
        // 메모 아래 작은 버튼: 지금까지 쓴 모든 메모 보기·관리
        item(key = "all-memos") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(
                    onClick = { viewModel.flushMemo(); onOpenAllMemos() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("전체 메모 보기", style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        val day = state.day
        item {
            SectionHeader(dayTitle(day), modifier = Modifier.coachTarget("home.add")) {
                // 오늘:            [일정 추가] [루틴 만들기]
                // 앞으로의 날짜:   [일정 추가] [루틴 추가] [오늘로]
                // 지난 날짜:       [루틴 추가] [오늘로]   (지난 날짜에는 일정을 만들지 않음)
                // 버튼이 세 개일 때는 제목과 한 줄에 들어가도록 아이콘을 빼고 여백을 줄임
                val otherDay = day != null && !day.isToday
                val canAddSchedule = day == null || !day.isPast
                val compact = otherDay && canAddSchedule
                if (canAddSchedule) {
                    CompactTextButton(
                        text = "일정 추가",
                        icon = if (compact) null else Icons.Rounded.Event,
                        compact = compact,
                        onClick = { onNewSchedule(day?.date ?: LocalDate.now()) }
                    )
                }
                if (otherDay) {
                    CompactTextButton(text = "루틴 추가", compact = compact, onClick = onNewRoutine)
                    CompactTextButton(text = "오늘로", compact = compact, onClick = viewModel::backToToday)
                } else {
                    CompactTextButton(text = "루틴 만들기", icon = Icons.Rounded.Add, onClick = onNewRoutine)
                }
            }
        }

        val schedules = day?.schedules.orEmpty()
        val routines = day?.repeating.orEmpty()
        if (day != null && schedules.isEmpty() && routines.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            if (day.isToday) "오늘 예정된 일정과 루틴이 없어요" else "이 날 예정된 일정과 루틴이 없어요",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "날짜를 정해 한 번만 울리는 일정이나 요일마다 반복되는 루틴을 만들 수 있어요. " +
                                "시간이 되면 알려주고, 등록한 앱이 챗헤드 맨 앞에 올라와요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        // v0.5.0: 그 날짜의 일정이 맨 위, 그 아래 요일 반복 루틴
        if (schedules.isNotEmpty()) {
            item(key = "schedules-label") { ListLabel("일정") }
            items(schedules, key = { it.routine.routineId }) { today ->
                TodayRoutineCard(
                    today = today,
                    isPast = day?.isPast == true,
                    canCheck = day?.isFuture != true,
                    onOpen = { onOpenRoutine(today.routine.routineId) },
                    onToggle = { pkg, checked -> viewModel.toggleCheck(today.routine.routineId, pkg, checked) }
                )
                Spacer(Modifier.height(10.dp))
            }
            if (routines.isNotEmpty()) item(key = "routines-label") { ListLabel("반복 루틴") }
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

/** 섹션 머리말 오른쪽의 작은 글자 버튼 (버튼 두 개가 한 줄에 들어가도록 여백을 줄임) */
@Composable
private fun CompactTextButton(text: String, onClick: () -> Unit, icon: ImageVector? = null, compact: Boolean = false) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp),
        modifier = if (compact) Modifier.defaultMinSize(minWidth = 1.dp) else Modifier
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text)
    }
}

/** 일정 / 반복 루틴 구분 글씨 */
@Composable
private fun ListLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

/** 홈 화면 코치마크 가이드 (앱을 처음 열 때) */
private fun homeTourSteps(
    scrollTop: suspend () -> Unit,
    scrollToDayHeader: suspend () -> Unit
): List<CoachStep> = listOf(
    CoachStep(
        target = null,
        title = "루틴 체크에 오신 걸 환영해요 👋",
        body = "챗헤드로 자주 쓰는 앱을 바로 열고, 매일 할 루틴을 놓치지 않게 도와주는 앱이에요. 주요 기능을 1분 안에 보여드릴게요.",
        prepare = scrollTop
    ),
    CoachStep(
        target = "home.chathead",
        title = "챗헤드",
        body = "켜 두면 어느 화면에서든 작은 동그라미가 떠 있어요. 누르면 앱 목록이 펼쳐지고, 화면 끝까지 밀면 가장자리 바로 숨어요. 아래쪽 X로 끌어다 놓으면 꺼져요.",
        prepare = scrollTop
    ),
    CoachStep(
        target = "home.chathead.toggle",
        title = "챗헤드 켜기·끄기",
        body = "여기서 챗헤드를 켜고 꺼요. 처음 켤 때 '다른 앱 위에 표시' 권한을 물어봐요."
    ),
    CoachStep(
        target = "home.chathead.items",
        title = "챗헤드 항목",
        body = "챗헤드에 띄울 앱·웹사이트·폴더를 고르고 순서를 정해요. 루틴 시간에는 그 루틴의 앱이 맨 앞에 와요."
    ),
    CoachStep(
        target = "home.week",
        title = "주간 달력",
        body = "날짜를 누르면 그 날의 루틴과 메모가 보여요. 위쪽 ‹ › 로 지난주·다음 주로 넘어가요."
    ),
    CoachStep(
        target = "home.memo",
        title = "날짜별 메모",
        body = "고른 날짜의 할 일이나 생각을 적어요. 입력하면 바로 자동 저장돼요."
    ),
    CoachStep(
        target = "home.add",
        title = "일정·루틴 만들기",
        body = "루틴은 고른 요일마다 반복되고, 일정은 정한 날짜에 한 번만 실행돼요. 시간이 되면 알려주고, 등록한 앱이 챗헤드 맨 앞에 올라와요.",
        prepare = scrollToDayHeader
    ),
    CoachStep(
        target = "nav.tabs",
        title = "루틴 · 통계 탭",
        body = "루틴 탭에서 모든 루틴과 일정을 관리하고, 통계 탭에서 달성률과 연속 기록, 배지를 확인해요."
    ),
    CoachStep(
        target = "home.settings",
        title = "설정",
        body = "챗헤드 모양, 알림 방식, 권한을 바꿀 수 있어요. 이 가이드도 설정에서 언제든 다시 볼 수 있어요.",
        prepare = scrollTop
    )
)

/** "오늘의 루틴" / "10월 13일 (화) 루틴" */
private fun dayTitle(day: com.routinecheck.app.data.repository.TodaySnapshot?): String {
    if (day == null || day.isToday) return "오늘의 루틴"
    val d = day.date
    return "${d.monthValue}월 ${d.dayOfMonth}일 (${DayMask.shortName(d.dayOfWeek)}) 루틴"
}

/** 닫힌 메모지 자리: 얇은 카드 버튼 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoOpenButton(date: LocalDate, hasMemo: Boolean, onClick: () -> Unit) {
    val ink = Color(0xFF3B3320)
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFE8D48A)),
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFFF4C9).copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (hasMemo) Icons.Rounded.EditNote else Icons.Rounded.Add, contentDescription = null, tint = ink)
            Spacer(Modifier.width(8.dp))
            Text(
                "${date.monthValue}월 ${date.dayOfMonth}일 (${DayMask.shortName(date.dayOfWeek)}) " + if (hasMemo) "메모 보기" else "메모 작성",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = ink
            )
        }
    }
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).coachTarget("home.chathead")
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
                        modifier = Modifier.coachTarget("home.chathead.toggle"),
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
                    FilledTonalButton(onClick = onManageApps, modifier = Modifier.coachTarget("home.chathead.items")) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("항목 관리")
                    }
//                    FilledTonalButton(onClick = onOpenSettings) {
//                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
//                        Spacer(Modifier.width(4.dp))
//                        Text("챗헤드 설정")
//                    }
                }
            }
        }
    }
}

/** ‹  이번 주 · 10월 5일 – 11일  ›   (이번 주가 아니면 "이번 주로" 버튼) */
@Composable
private fun WeekHeader(
    cells: List<WeekDayCell>,
    offset: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit
) {
    val first = cells.firstOrNull()?.date
    val last = cells.lastOrNull()?.date
    val label = when (offset) {
        0L -> "이번 주"
        1L -> "다음 주"
        -1L -> "지난주"
        else -> if (offset > 0) "${offset}주 뒤" else "${-offset}주 전"
    }
    val range = if (first != null && last != null) {
        if (first.monthValue == last.monthValue) "${first.monthValue}월 ${first.dayOfMonth}일 – ${last.dayOfMonth}일"
        else "${first.monthValue}월 ${first.dayOfMonth}일 – ${last.monthValue}월 ${last.dayOfMonth}일"
    } else ""
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "지난주")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "다음 주")
        }
    }
    if (offset != 0L) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onThisWeek, modifier = Modifier.height(32.dp)) {
                Text("이번 주로 돌아가기", style = MaterialTheme.typography.labelMedium)
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
        "● 파랑: 루틴·일정 있음  ● 노랑: 메모 있음 · 날짜를 누르면 그 날의 메모와 일정·루틴을 볼 수 있어요",
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
    val isSchedule = routine.isSchedule
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        // 일정은 반복 루틴과 구분되게 다른 바탕색
        colors = if (isSchedule) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSchedule) {
                    Icon(
                        Icons.Rounded.Event,
                        contentDescription = "일정",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(end = 10.dp).size(22.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isSchedule) "${TimeText.display(routine.startTime)} · 일정 (한 번)"
                        else "${TimeText.display(routine.startTime)} · ${DayMask.label(routine.daysOfWeek)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    // 앱 없는 일정: 확인할 앱이 없으니 시각만 기준으로 표시
                    isSchedule && today.apps.isEmpty() ->
                        if (today.hasStarted) StatusChip("시간 지남", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        else StatusChip("예정", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
                    today.isComplete -> StatusChip("완료", Brand.Green.copy(alpha = 0.18f), Brand.Green)
                    isPast -> StatusChip("미완료", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    today.hasStarted -> StatusChip("진행 중", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    else -> StatusChip("예정", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            routine.message?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(
                    "💬 $msg",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (today.apps.isEmpty()) {
                if (isSchedule) return@Column // 일정은 앱 없이 알림·메시지만 받아도 됨
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
