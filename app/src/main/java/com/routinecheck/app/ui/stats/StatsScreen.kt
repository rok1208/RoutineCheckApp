package com.routinecheck.app.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.routinecheck.app.data.repository.DayStat
import com.routinecheck.app.data.repository.RoutineStat
import com.routinecheck.app.data.repository.StatsRepository
import com.routinecheck.app.data.repository.StatsSummary
import com.routinecheck.app.domain.AchievementBadge
import com.routinecheck.app.domain.AchievementMetrics
import com.routinecheck.app.domain.Cheer
import com.routinecheck.app.domain.CheerMessages
import com.routinecheck.app.domain.DayMask
import com.routinecheck.app.domain.RoutineLevel
import com.routinecheck.app.domain.StatsPeriod
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import com.routinecheck.app.ui.Brand
import com.routinecheck.app.ui.common.CoachStep
import com.routinecheck.app.ui.common.CoachTour
import com.routinecheck.app.ui.common.SectionHeader
import com.routinecheck.app.ui.common.coachTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// ─────────────────────────────── 배지 기록

/**
 * 얻은 배지와 지금까지의 최고 값을 기기에 저장합니다.
 * 통계는 최근 120일만 보지만, 한 번 얻은 배지와 레벨은 시간이 지나거나 루틴을 지워도 내려가지 않게 하기 위해서예요.
 */
private class AchievementStore(context: Context) {
    private val prefs = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)

    fun earnedOn(badge: AchievementBadge): LocalDate? =
        prefs.getString("badge_" + badge.name, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun markEarned(badge: AchievementBadge, date: LocalDate) {
        prefs.edit().putString("badge_" + badge.name, date.toString()).apply()
    }

    /** 저장된 최고 값과 비교해 큰 값을 남기고 돌려줌 */
    fun keepMax(key: String, value: Int): Int {
        val stored = prefs.getInt("max_$key", 0)
        if (value > stored) prefs.edit().putInt("max_$key", value).apply()
        return maxOf(stored, value)
    }
}

data class BadgeUi(
    val badge: AchievementBadge,
    val earnedOn: LocalDate?,
    val current: Int,
    val progress: Float
) {
    val earned: Boolean get() = earnedOn != null
}

data class StatsUiState(
    val summary: StatsSummary,
    val cheer: Cheer,
    val checks: Int,
    val level: RoutineLevel,
    val levelProgress: Float,
    /** 지금까지 가장 길었던 연속 일수 (저장된 최고 값 포함) */
    val bestStreak: Int,
    val badges: List<BadgeUi>
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: StatsRepository,
    @ApplicationContext context: Context
) : ViewModel() {
    private val store = AchievementStore(context)

    /** 이번에 새로 얻은 배지 (축하 창) */
    private val _celebrate = MutableStateFlow<List<AchievementBadge>>(emptyList())
    val celebrate: StateFlow<List<AchievementBadge>> = _celebrate.asStateFlow()

    val uiState: StateFlow<StatsUiState?> = statsRepository.observeSummary()
        .map { s -> buildState(s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissCelebration() { _celebrate.value = emptyList() }

    private fun buildState(s: StatsSummary): StatsUiState {
        val metrics = AchievementMetrics(
            checks = store.keepMax("checks", s.totalChecks),
            perfectDays = store.keepMax("perfect_days", s.perfectDays),
            bestStreak = store.keepMax("best_streak", s.bestStreak)
        )
        val today = LocalDate.now()
        val newly = mutableListOf<AchievementBadge>()
        val badges = AchievementBadge.entries.map { badge ->
            var earnedOn = store.earnedOn(badge)
            if (earnedOn == null && badge.isMet(metrics)) {
                store.markEarned(badge, today)
                earnedOn = today
                newly += badge
            }
            BadgeUi(badge, earnedOn, metrics.valueOf(badge.metric), badge.progress(metrics))
        }
        if (newly.isNotEmpty()) _celebrate.value = _celebrate.value + newly
        return StatsUiState(
            summary = s,
            cheer = CheerMessages.of(
                todayTotal = s.today.total,
                todayDone = s.today.done,
                streak = s.streakDays,
                best = maxOf(metrics.bestStreak, s.streakDays),
                hasAnyRoutine = s.hasAnyRoutine
            ),
            checks = metrics.checks,
            level = RoutineLevel.of(metrics.checks),
            levelProgress = RoutineLevel.progress(metrics.checks),
            bestStreak = maxOf(metrics.bestStreak, s.streakDays),
            badges = badges
        )
    }
}

/** 막대 색: 한 가지 색(완료율 = 크기). 다크 모드는 어두운 배경 기준으로 따로 검증한 단계 사용 */
private val BarLight = Color(0xFF14A38B)
private val BarDark = Color(0xFF1FA68B)
private val Flame = Color(0xFFFF8A3D)

/** 연속 기록 다음 목표 */
private val STREAK_MILESTONES = listOf(3, 7, 14, 30, 60, 100, 200, 365)

/**
 * 통계 · 성취 (v0.6.0)
 *  ① 레벨 + 오늘의 한마디 (누르면 전체 등급표)  ② 연속 기록  ③ 오늘 · 시작 이후 · 완벽한 날
 *  ④ 주간 달성도 (‹ › 로 지난 주 보기)  ⑤ 배지  ⑥ 루틴별 연속 기록
 * 모든 기록은 앱을 설치한 날부터 셉니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val celebrate by viewModel.celebrate.collectAsStateWithLifecycle()
    var badgeDetail by remember { mutableStateOf<BadgeUi?>(null) }
    var showLevels by remember { mutableStateOf(false) }

    CoachTour(id = "stats", enabled = ui != null, steps = statsTourSteps())

    Scaffold(topBar = { TopAppBar(title = { Text("통계") }) }) { padding ->
        val state = ui ?: return@Scaffold
        val s = state.summary
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "${s.startDate.monthValue}월 ${s.startDate.dayOfMonth}일 시작 · ${s.dayNumber}일째 기록 중",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HeroCard(state, onClick = { showLevels = true }, modifier = Modifier.coachTarget("stats.hero"))
            Spacer(Modifier.height(12.dp))
            StreakCard(s, bestStreak = state.bestStreak, modifier = Modifier.coachTarget("stats.streak"))

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "오늘",
                    if (s.today.hasRoutine) "${s.today.done}/${s.today.total}" else "—",
                    if (s.today.hasRoutine) "앱 확인" else "예정 루틴 없음",
                    Modifier.weight(1f)
                )
                StatTile(
                    "시작 이후",
                    s.overallRate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    "${s.dayNumber}일 평균",
                    Modifier.weight(1f)
                )
                StatTile("완벽한 날", "${s.perfectDays}일", "모두 끝낸 날", Modifier.weight(1f))
            }

            Column(Modifier.coachTarget("stats.week")) {
                SectionHeader("주간 달성도")
                WeekPager(s)
            }

            Column(Modifier.coachTarget("stats.badges")) {
                SectionHeader("배지 ${state.badges.count { it.earned }}/${state.badges.size}")
                BadgeGrid(state.badges, onClick = { badgeDetail = it })
            }

            if (s.perRoutine.isNotEmpty()) {
                SectionHeader("루틴별 연속 기록")
                s.perRoutine.forEach { RoutineRow(it) }
            }
            Spacer(Modifier.height(32.dp))
        }

        if (showLevels) LevelDialog(checks = state.checks, onDismiss = { showLevels = false })
    }

    if (celebrate.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCelebration,
            title = { Text(if (celebrate.size == 1) "새 배지를 얻었어요! 🎉" else "배지 ${celebrate.size}개를 얻었어요! 🎉") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    celebrate.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.emoji, fontSize = 30.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(b.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(b.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissCelebration) { Text("좋아요") } }
        )
    }

    badgeDetail?.let { b ->
        AlertDialog(
            onDismissRequest = { badgeDetail = null },
            icon = { Text(b.badge.emoji, fontSize = 36.sp, modifier = Modifier.alpha(if (b.earned) 1f else 0.4f)) },
            title = { Text(b.badge.title) },
            text = {
                Text(
                    if (b.earned) "${b.badge.description}\n${b.earnedOn!!.monthValue}월 ${b.earnedOn.dayOfMonth}일에 얻었어요."
                    else "${badgeGoalText(b.badge)}\n지금 ${b.current} / ${b.badge.target}",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { badgeDetail = null }) { Text("닫기") } }
        )
    }
}

private fun badgeGoalText(badge: AchievementBadge): String = when (badge.metric) {
    com.routinecheck.app.domain.BadgeMetric.CHECKS -> "루틴 앱을 ${badge.target}번 확인하면 얻어요."
    com.routinecheck.app.domain.BadgeMetric.PERFECT_DAYS -> "루틴을 모두 끝낸 날이 ${badge.target}일이 되면 얻어요."
    com.routinecheck.app.domain.BadgeMetric.BEST_STREAK -> "${badge.target}일 연속으로 루틴을 모두 끝내면 얻어요."
}

private fun statsTourSteps(): List<CoachStep> = listOf(
    CoachStep(
        target = "stats.hero",
        title = "내 등급과 오늘의 한마디",
        body = "루틴 앱을 확인할수록 등급이 올라가요. 카드를 누르면 전체 등급과 올라가는 조건을 볼 수 있어요."
    ),
    CoachStep(
        target = "stats.streak",
        title = "연속 기록",
        body = "예정된 루틴을 모두 끝낸 날이 며칠 이어졌는지 보여줘요. 루틴이 없는 날은 기록이 끊기지 않아요."
    ),
    CoachStep(
        target = "stats.week",
        title = "주간 달성도",
        body = "요일마다 얼마나 끝냈는지 막대로 보여줘요. ‹ 버튼으로 지난 주 기록을 넘겨 볼 수 있어요."
    ),
    CoachStep(
        target = "stats.badges",
        title = "배지",
        body = "연속 기록이나 확인 횟수 목표를 넘으면 배지를 얻어요. 눌러서 얻는 방법을 볼 수 있어요."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroCard(state: StatsUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val level = state.level
    val total = RoutineLevel.entries.size
    Card(shape = RoundedCornerShape(24.dp), onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Brand.Mint, Brand.Blue)))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(level.emoji, fontSize = 28.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(level.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${total}단계 중 ${level.ordinal + 1}단계 · 앱 ${state.checks}번 확인",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // 누르면 전체 등급표가 열린다는 표시
                Surface(color = Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(50)) {
                    Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("등급표", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            // 전체 단계 중 위치: 단계 점
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 14.dp)) {
                RoutineLevel.entries.forEach { l ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    l.ordinal < level.ordinal -> Color.White
                                    l == level -> Color.White.copy(alpha = 0.3f + 0.7f * state.levelProgress)
                                    else -> Color.White.copy(alpha = 0.25f)
                                }
                            )
                    )
                }
            }
            val next = level.next
            Text(
                if (next != null) "다음 등급 '${next.title}'까지 앱 ${next.minChecks - state.checks}번 더 확인" else "최고 등급이에요!",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(state.cheer.headline, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.cheer.detail, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** 전체 등급표: 몇 단계 중 어디인지, 각 등급의 조건 */
@Composable
private fun LevelDialog(checks: Int, onDismiss: () -> Unit) {
    val current = RoutineLevel.of(checks)
    val levels = RoutineLevel.entries
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("등급표 (${levels.size}단계)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "루틴 앱을 확인(체크)할 때마다 1씩 쌓이고, 쌓인 수로 등급이 올라가요. 지금까지 ${checks}번 확인했어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                levels.forEach { l ->
                    val isCurrent = l == current
                    val reached = l.ordinal <= current.ordinal
                    val next = l.next
                    Surface(
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(l.emoji, fontSize = 24.sp, modifier = Modifier.alpha(if (reached) 1f else 0.35f))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${l.ordinal + 1}단계 · ${l.title}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    if (next != null) "앱 확인 ${l.minChecks}~${next.minChecks - 1}번" else "앱 확인 ${l.minChecks}번 이상",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isCurrent && next != null) {
                                    LinearProgressIndicator(
                                        progress = { RoutineLevel.progress(checks) },
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                                    )
                                    Text(
                                        "다음 단계까지 ${next.minChecks - checks}번",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            when {
                                isCurrent -> Text("지금 여기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                reached -> Icon(Icons.Rounded.Check, contentDescription = "달성", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun StreakCard(s: StatsSummary, bestStreak: Int, modifier: Modifier = Modifier) {
    val streak = s.streakDays
    val nextGoal = STREAK_MILESTONES.firstOrNull { it > streak }
    val prevGoal = STREAK_MILESTONES.lastOrNull { it <= streak } ?: 0
    val today = LocalDate.now()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 34.sp, modifier = Modifier.alpha(if (streak > 0) 1f else 0.35f))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (streak > 0) "${streak}일 연속" else "연속 기록 0일",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "루틴을 모두 끝낸 날 · 최고 ${bestStreak}일" + if (streak > 0 && streak >= bestStreak) " (최고 기록 중!)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (nextGoal != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { ((streak - prevGoal).toFloat() / (nextGoal - prevGoal)).coerceIn(0f, 1f) },
                    color = Flame,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                Text(
                    "${nextGoal}일 연속까지 ${nextGoal - streak}일 남았어요",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // 최근 7일: 모두 끝낸 날은 불꽃, 일부만 한 날은 점, 루틴 없는 날·시작 전은 빈칸
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                (6 downTo 0).forEach { back ->
                    val date = today.minusDays(back.toLong())
                    val day = s.dayOf(date)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            when {
                                day == null -> Text("·", color = MaterialTheme.colorScheme.outlineVariant)
                                day.isPerfect -> Text("🔥", fontSize = 18.sp)
                                day.hasRoutine && day.done > 0 -> Box(Modifier.size(10.dp).background(Flame.copy(alpha = 0.45f), CircleShape))
                                day.hasRoutine -> Box(Modifier.size(10.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                else -> Text("–", color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        Text(
                            DayMask.shortName(date.dayOfWeek),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, caption: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 주간 막대 한 칸 */
private data class WeekCell(val date: LocalDate, val stat: DayStat?, val beforeStart: Boolean, val future: Boolean)

/**
 * 주간 달성도: 월~일 한 주씩. ‹ 로 지난 주, › 로 다음 주(이번 주까지).
 * 설치 전 주로는 넘어가지 않고, 설치 전 날짜는 "시작 전"으로 표시합니다.
 */
@Composable
private fun WeekPager(s: StatsSummary) {
    val today = LocalDate.now()
    val thisWeek = StatsPeriod.weekStart(today)
    val firstWeek = StatsPeriod.weekStart(s.startDate)
    var offset by remember { mutableIntStateOf(0) }
    val weekStart = thisWeek.minusWeeks(offset.toLong())
    val weekEnd = weekStart.plusDays(6)
    val canPrev = weekStart.isAfter(firstWeek)
    val canNext = offset > 0

    val cells = (0..6).map { i ->
        val d = weekStart.plusDays(i.toLong())
        WeekCell(d, s.dayOf(d), beforeStart = d.isBefore(s.startDate), future = d.isAfter(today))
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { offset++ }, enabled = canPrev) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "지난 주")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (offset) { 0 -> "이번 주"; 1 -> "지난주"; else -> "${offset}주 전" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${weekStart.monthValue}월 ${weekStart.dayOfMonth}일 – " +
                    (if (weekEnd.monthValue != weekStart.monthValue) "${weekEnd.monthValue}월 " else "") + "${weekEnd.dayOfMonth}일",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { offset-- }, enabled = canNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "다음 주")
        }
    }

    // 이 주 평균 (+ 바로 전 주와 비교, 전 주가 설치 전이면 비교하지 않음)
    val rate = s.rateBetween(weekStart, weekEnd)
    val prevRate = if (weekStart.minusDays(1).isBefore(s.startDate)) null
    else s.rateBetween(weekStart.minusWeeks(1), weekStart.minusDays(1))
    val summary = when {
        rate == null -> "이 주에는 예정된 루틴 기록이 없어요"
        prevRate == null -> "이 주 평균 ${(rate * 100).roundToInt()}%"
        else -> {
            val now = (rate * 100).roundToInt()
            val diff = now - (prevRate * 100).roundToInt()
            when {
                diff > 0 -> "이 주 평균 ${now}% · 전 주보다 ${diff}%p 올랐어요 📈"
                diff < 0 -> "이 주 평균 ${now}% · 전 주보다 ${-diff}%p 낮아요"
                else -> "이 주 평균 ${now}% · 전 주만큼 꾸준해요"
            }
        }
    }
    Text(
        summary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    )
    WeekChart(cells)
}

/** 7일 세로 막대. 막대 하나 = 하루 완료율(0~100%). 막대를 누르면 그날 상세 */
@Composable
private fun WeekChart(cells: List<WeekCell>) {
    var selected by remember(cells.first().date) {
        mutableIntStateOf(cells.indexOfLast { !it.future && !it.beforeStart }.coerceAtLeast(0))
    }
    val barColor = if (isSystemInDarkTheme()) BarDark else BarLight
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val today = LocalDate.now()

    val sel = cells[selected]
    val selStat = sel.stat
    Text(
        text = "${sel.date.monthValue}월 ${sel.date.dayOfMonth}일 (${DayMask.shortName(sel.date.dayOfWeek)}) · " + when {
            sel.beforeStart -> "앱 시작 전"
            sel.future -> "아직 오지 않은 날"
            selStat != null && selStat.hasRoutine -> "${selStat.done}/${selStat.total} 확인 · ${(selStat.rate * 100).roundToInt()}%"
            else -> "예정된 루틴 없음"
        },
        style = MaterialTheme.typography.bodySmall,
        color = muted,
        modifier = Modifier.padding(bottom = 6.dp)
    )

    Row(Modifier.fillMaxWidth().height(150.dp)) {
        cells.forEachIndexed { index, cell ->
            val isSelected = index == selected
            val stat = cell.stat
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selected = index }
                    .semantics {
                        contentDescription = "${cell.date.monthValue}월 ${cell.date.dayOfMonth}일 " + when {
                            cell.beforeStart -> "시작 전"
                            cell.future -> "예정"
                            stat != null && stat.hasRoutine -> "완료율 ${(stat.rate * 100).roundToInt()}%"
                            else -> "루틴 없음"
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val w = size.width
                    val h = size.height
                    drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
                    val barWidth = minOf(24.dp.toPx(), w * 0.6f)
                    val left = (w - barWidth) / 2
                    if (isSelected) drawRect(trackColor, Offset(left - 4.dp.toPx(), 0f), Size(barWidth + 8.dp.toPx(), h))
                    if (stat != null && stat.hasRoutine && stat.rate > 0f) {
                        val barH = (h * stat.rate).coerceAtLeast(4.dp.toPx())
                        val r = 4.dp.toPx()
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = left, top = h - barH, right = left + barWidth, bottom = h,
                                    topLeftCornerRadius = CornerRadius(r, r),
                                    topRightCornerRadius = CornerRadius(r, r),
                                    bottomLeftCornerRadius = CornerRadius.Zero,
                                    bottomRightCornerRadius = CornerRadius.Zero
                                )
                            )
                        }
                        drawPath(path, barColor.copy(alpha = if (stat.isPerfect) 1f else 0.7f))
                    }
                }
                Text(
                    when {
                        cell.beforeStart -> "–"
                        stat?.isPerfect == true -> "✓"
                        stat != null && stat.hasRoutine -> "${(stat.rate * 100).roundToInt()}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stat?.isPerfect == true) barColor else muted,
                    fontWeight = if (stat?.isPerfect == true) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    DayMask.shortName(cell.date.dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (cell.date == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (cell.beforeStart || cell.future) muted.copy(alpha = 0.5f) else if (isSelected) MaterialTheme.colorScheme.onSurface else muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
    Text(
        "막대 = 그날 끝낸 비율 · 아래 숫자 % · ✓ 모두 끝낸 날",
        style = MaterialTheme.typography.labelSmall,
        color = muted,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** 배지 4개씩: 얻은 배지는 또렷하게, 못 얻은 배지는 흐리게 + 진행도 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BadgeGrid(badges: List<BadgeUi>, onClick: (BadgeUi) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { b ->
                    Card(
                        onClick = { onClick(b) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (b.earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(b.badge.emoji, fontSize = 26.sp, modifier = Modifier.alpha(if (b.earned) 1f else 0.3f))
                            Text(
                                b.badge.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (b.earned) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            if (!b.earned) {
                                Text(
                                    "${minOf(b.current, b.badge.target)}/${b.badge.target}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 루틴별: 퍼센트 대신 연속 기록만 짧게 */
@Composable
private fun RoutineRow(stat: RoutineStat) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stat.routine.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            when {
                stat.scheduledDays == 0 -> "아직 기록 없음"
                stat.streak >= 1 -> "🔥 ${stat.streak}번 연속" + if (stat.bestStreak > stat.streak) " · 최고 ${stat.bestStreak}" else ""
                stat.bestStreak >= 1 -> "최고 ${stat.bestStreak}번 연속"
                else -> "첫 완료를 기다려요"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (stat.streak >= 1) FontWeight.SemiBold else FontWeight.Normal,
            color = if (stat.streak >= 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
