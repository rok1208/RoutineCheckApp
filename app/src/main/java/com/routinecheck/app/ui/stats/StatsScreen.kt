package com.routinecheck.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.repository.DayStat
import com.routinecheck.app.data.repository.StatsRepository
import com.routinecheck.app.data.repository.StatsSummary
import com.routinecheck.app.ui.common.SectionHeader
import com.routinecheck.app.util.DayMask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class StatsViewModel @Inject constructor(statsRepository: StatsRepository) : ViewModel() {
    val summary: StateFlow<StatsSummary?> = statsRepository.observeSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** 막대 색: 한 가지 색(완료율 = 크기). 다크 모드는 어두운 배경 기준으로 따로 검증한 단계 사용 */
private val BarLight = Color(0xFF14A38B)
private val BarDark = Color(0xFF1FA68B)

/** (선택) 통계 / 완료율 리포트 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("통계") }) }) { padding ->
        val s = summary ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            val today = s.week.lastOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "오늘",
                    if (today?.hasRoutine == true) "${(today.rate * 100).roundToInt()}%" else "—",
                    if (today?.hasRoutine == true) "${today.done}/${today.total} 확인" else "예정 루틴 없음",
                    Modifier.weight(1f)
                )
                StatTile("최근 30일", "${(s.monthRate * 100).roundToInt()}%", "평균 완료율", Modifier.weight(1f))
                StatTile("연속 달성", "${s.streakDays}일", "100% 완료한 날", Modifier.weight(1f))
            }

            SectionHeader("최근 7일 완료율")
            WeekChart(s.week)

            SectionHeader("루틴별 (최근 30일)")
            if (s.perRoutine.isEmpty()) {
                Text("앱이 등록된 루틴이 없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.perRoutine.forEach { stat ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row {
                        Text(stat.routine.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            if (stat.total == 0) "기록 없음" else "${(stat.rate * 100).roundToInt()}%  (${stat.done}/${stat.total})",
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
                }
            }
            Spacer(Modifier.height(32.dp))
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
