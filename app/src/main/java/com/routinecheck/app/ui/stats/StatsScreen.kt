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
 *  ① 레벨 + 오늘의 한마디  ② 연속 기록(현재·최고·다음 목표)  ③ 오늘·30일·완벽한 날
 *  ④ 최근 7일(지난주와 비교)  ⑤ 5주 달력  ⑥ 배지  ⑦ 루틴별 완료율·연속
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val celebrate by viewModel.celebrate.collectAsStateWithLifecycle()
    var badgeDetail by remember { mutableStateOf<BadgeUi?>(null) }

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
            HeroCard(state, Modifier.coachTarget("stats.hero"))
            Spacer(Modifier.height(12.dp))
            StreakCard(s, bestStreak = state.bestStreak, modifier = Modifier.coachTarget("stats.streak"))

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "오늘",
                    if (s.today.hasRoutine) "${(s.today.rate * 100).roundToInt()}%" else "—",
                    if (s.today.hasRoutine) "${s.today.done}/${s.today.total} 확인" else "예정 루틴 없음",
                    Modifier.weight(1f)
                )
                StatTile("최근 30일", "${(s.monthRate * 100).roundToInt()}%", "평균 완료율", Modifier.weight(1f))
                StatTile("완벽한 날", "${s.perfectDays}일", "모두 끝낸 날", Modifier.weight(1f))
            }

            SectionHeader("최근 7일")
            WeekCompare(s.weekRate, s.prevWeekRate)
            WeekChart(s.week)

            // 가이드에서 제목만이 아니라 달력·배지 전체를 강조하도록 함께 묶음
            Column(Modifier.coachTarget("stats.calendar")) {
                SectionHeader("5주 달력")
                MonthCalendar(s.calendar)
            }

            Column(Modifier.coachTarget("stats.badges")) {
                SectionHeader("배지 ${state.badges.count { it.earned }}/${state.badges.size}")
                BadgeGrid(state.badges, onClick = { badgeDetail = it })
            }

            SectionHeader("루틴별 (최근 30일)")
            if (s.perRoutine.isEmpty()) {
                Text("앱이 등록된 루틴이 없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.perRoutine.forEach { RoutineRow(it) }
            Spacer(Modifier.height(32.dp))
        }
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
        title = "레벨과 오늘의 한마디",
        body = "루틴 앱을 확인할수록 레벨이 올라가요. 아래 한마디는 오늘 얼마나 했는지에 맞춰 바뀌어요."
    ),
    CoachStep(
        target = "stats.streak",
        title = "연속 기록",
        body = "예정된 루틴을 모두 끝낸 날이 며칠 이어졌는지 보여줘요. 루틴이 없는 날은 기록이 끊기지 않아요."
    ),
    CoachStep(
        target = "stats.calendar",
        title = "5주 달력",
        body = "진한 칸일수록 많이 끝낸 날이에요. ✓ 표시는 루틴을 모두 끝낸 날이에요."
    ),
    CoachStep(
        target = "stats.badges",
        title = "배지",
        body = "연속 기록이나 확인 횟수 목표를 넘으면 배지를 얻어요. 눌러서 얻는 방법을 볼 수 있어요."
    )
)

@Composable
private fun HeroCard(state: StatsUiState, modifier: Modifier = Modifier) {
    val level = state.level
    Card(shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
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
                    Text("Lv.${level.ordinal + 1} ${level.title}", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("지금까지 앱 ${state.checks}번 확인", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.levelProgress },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            val next = level.next
            Text(
                if (next != null) "다음 레벨 '${next.title}'까지 ${next.minChecks - state.checks}번" else "최고 레벨이에요!",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(state.cheer.headline, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.cheer.detail, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun StreakCard(s: StatsSummary, bestStreak: Int, modifier: Modifier = Modifier) {
    val streak = s.streakDays
    val nextGoal = STREAK_MILESTONES.firstOrNull { it > streak }
    val prevGoal = STREAK_MILESTONES.lastOrNull { it <= streak } ?: 0
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
            // 최근 7일: 모두 끝낸 날은 불꽃, 일부만 한 날은 점, 루틴 없는 날은 빈칸
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                s.week.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            when {
                                day.isPerfect -> Text("🔥", fontSize = 18.sp)
                                day.hasRoutine && day.done > 0 -> Box(Modifier.size(10.dp).background(Flame.copy(alpha = 0.45f), CircleShape))
                                day.hasRoutine -> Box(Modifier.size(10.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                else -> Text("–", color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        Text(
                            DayMask.shortName(day.date.dayOfWeek),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (day.date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
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

/** "이번 주 82% · 지난주보다 12%p 올랐어요" */
@Composable
private fun WeekCompare(rate: Float?, prev: Float?) {
    if (rate == null) return
    val now = (rate * 100).roundToInt()
    val text = if (prev == null) {
        "이번 주 평균 ${now}%"
    } else {
        val diff = now - (prev * 100).roundToInt()
        when {
            diff > 0 -> "이번 주 평균 ${now}% · 지난주보다 ${diff}%p 올랐어요 📈"
            diff < 0 -> "이번 주 평균 ${now}% · 지난주보다 ${-diff}%p 낮아요. 다시 올려 봐요!"
            else -> "이번 주 평균 ${now}% · 지난주만큼 꾸준해요"
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
}

/** 최근 5주 달력: 칸 색 진하기 = 완료율, ✓ = 모두 끝낸 날 */
@Composable
private fun MonthCalendar(days: List<DayStat>) {
    if (days.isEmpty()) return
    val base = if (isSystemInDarkTheme()) BarDark else BarLight
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val today = LocalDate.now()
    // 월요일부터 시작하도록 앞을 비움
    val lead = days.first().date.dayOfWeek.value - 1
    val cells: List<DayStat?> = List(lead) { null } + days
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayMask.ORDERED.forEach {
                Text(
                    DayMask.shortName(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day != null) {
                            val color = when {
                                !day.hasRoutine -> empty.copy(alpha = 0.5f)
                                day.done == 0 -> empty
                                else -> base.copy(alpha = 0.25f + 0.75f * day.rate)
                            }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color)
                                    .then(
                                        if (day.date == today) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .semantics {
                                        contentDescription = "${day.date.monthValue}월 ${day.date.dayOfMonth}일 " +
                                            if (day.hasRoutine) "완료율 ${(day.rate * 100).roundToInt()}%" else "루틴 없음"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (day.isPerfect) "✓" else "${day.date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (day.isPerfect) FontWeight.Bold else FontWeight.Normal,
                                    color = if (day.hasRoutine && day.rate >= 0.5f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "진할수록 많이 끝낸 날 · ✓ 모두 끝낸 날 · 테두리 오늘",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
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

@Composable
private fun RoutineRow(stat: RoutineStat) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.routine.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (stat.total == 0) "기록 없음" else "${(stat.rate * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { stat.rate },
            color = if (isSystemInDarkTheme()) BarDark else BarLight,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
        Text(
            buildString {
                append(CheerMessages.forRate(stat.rate, stat.total))
                if (stat.total > 0) append(" · ${stat.done}/${stat.total} 확인")
                if (stat.streak >= 2) append(" · 🔥 ${stat.streak}번 연속 완료")
                if (stat.bestStreak >= 2 && stat.bestStreak > stat.streak) append(" · 최고 ${stat.bestStreak}번")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * 7일 세로 막대. 막대 하나 = 하루 완료율(0~100%).
 * 막대를 누르면 위에 그날 상세가 보이고(기본: 오늘), 루틴이 없던 날은 막대 없이 "—".
 */
@Composable
private fun WeekChart(week: List<DayStat>) {
    if (week.isEmpty()) return
    var selected by remember(week) { mutableIntStateOf(week.lastIndex) }
    val barColor = if (isSystemInDarkTheme()) BarDark else BarLight
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val sel = week[selected]
    Text(
        text = "${sel.date.monthValue}월 ${sel.date.dayOfMonth}일 (${DayMask.shortName(sel.date.dayOfWeek)}) · " +
            if (sel.hasRoutine) "${sel.done}/${sel.total} 확인 · ${(sel.rate * 100).roundToInt()}%" else "예정된 루틴 없음",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(Modifier.fillMaxWidth().height(160.dp)) {
        // y축 눈금 (0 / 50 / 100%)
        Column(Modifier.fillMaxHeight().padding(bottom = 22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("100%", "50%", "0%").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.weight(1f).fillMaxHeight()) {
            week.forEachIndexed { index, day ->
                val isSelected = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selected = index }
                        .semantics {
                            contentDescription = "${day.date.monthValue}월 ${day.date.dayOfMonth}일 " +
                                if (day.hasRoutine) "완료율 ${(day.rate * 100).roundToInt()}%" else "루틴 없음"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(Modifier.weight(1f).fillMaxWidth()) {
                        val w = size.width
                        val h = size.height
                        // 가는 기준선 (0% / 50% / 100%)
                        listOf(0f, 0.5f, 1f).forEach { f ->
                            val y = h - h * f
                            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                        }
                        val barWidth = minOf(24.dp.toPx(), w * 0.6f)
                        val left = (w - barWidth) / 2
                        if (isSelected) {
                            // 선택된 날: 연한 트랙으로 강조 (막대 색은 그대로)
                            drawRect(trackColor, Offset(left - 4.dp.toPx(), 0f), Size(barWidth + 8.dp.toPx(), h))
                        }
                        if (day.hasRoutine && day.rate > 0f) {
                            val barH = (h * day.rate).coerceAtLeast(4.dp.toPx())
                            val r = 4.dp.toPx()
                            // 윗부분만 둥글게, 바닥은 각지게
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
                            drawPath(path, barColor)
                        }
                    }
                    Text(
                        DayMask.shortName(day.date.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (day.date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else muted,
                        maxLines = 1,
                        modifier = Modifier.height(22.dp).padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
