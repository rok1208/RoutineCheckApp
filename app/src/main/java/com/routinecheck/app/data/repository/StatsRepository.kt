package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.RoutineEntity
import com.routinecheck.app.data.local.occursOn
import com.routinecheck.app.domain.DateKey
import com.routinecheck.app.domain.Streaks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class DayStat(val date: LocalDate, val total: Int, val done: Int) {
    val rate: Float get() = if (total == 0) 0f else done.toFloat() / total
    val hasRoutine: Boolean get() = total > 0
    val isPerfect: Boolean get() = total > 0 && done >= total
}

data class RoutineStat(
    val routine: RoutineEntity,
    /** 최근 30일 확인해야 했던 앱 수 / 확인한 앱 수 */
    val total: Int,
    val done: Int,
    /** 이 루틴을 모두 끝낸 날이 이어진 횟수 (현재 / 최고) */
    val streak: Int,
    val bestStreak: Int
) {
    val rate: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class StatsSummary(
    val today: DayStat,
    /** 최근 7일 (오래된 날 → 오늘) */
    val week: List<DayStat>,
    /** 최근 35일 (5주 달력용, 오래된 날 → 오늘) */
    val calendar: List<DayStat>,
    /** 최근 30일 루틴별 완료율 */
    val perRoutine: List<RoutineStat>,
    /** 예정된 루틴을 100% 끝낸 날의 현재 연속 일수 (오늘이 아직 미완료면 어제부터) */
    val streakDays: Int,
    /** 최근 120일 중 가장 길었던 연속 일수 */
    val bestStreak: Int,
    /** 최근 120일 중 루틴을 모두 끝낸 날 수 */
    val perfectDays: Int,
    val monthRate: Float,
    /** 이번 7일 / 그 전 7일 완료율 (비교용, 기록이 없으면 null) */
    val weekRate: Float?,
    val prevWeekRate: Float?,
    /** 지금까지 확인한 앱 수 */
    val totalChecks: Int,
    /** 앱이 등록된 루틴이 하나라도 있는지 */
    val hasAnyRoutine: Boolean
)

/**
 * 완료율 · 연속 기록 통계. 체크 기록(routine_checklist_state)과 현재 루틴 구성을 바탕으로 계산합니다.
 * (과거에 루틴의 앱 구성이 바뀐 기록은 저장하지 않으므로 "현재 등록된 앱" 기준의 근사치입니다.)
 * v0.6.0: 연속 기록·최고 기록·완벽한 날을 위해 최근 120일을 봅니다.
 */
@Singleton
class StatsRepository @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    fun observeSummary(today: LocalDate = LocalDate.now()): Flow<StatsSummary> {
        val from = today.minusDays(WINDOW_DAYS - 1)
        return combine(
            routineRepository.observeRoutinesWithApps(),
            routineRepository.observeChecklistBetween(DateKey.of(from), DateKey.of(today)),
            routineRepository.observeCheckedCount()
        ) { routines, states, checkedCount ->
            val checked = states.filter { it.isChecked }
                .map { Triple(it.routineId, it.packageName, it.date) }
                .toHashSet()
            val zone = ZoneId.systemDefault()
            val active = routines.filter { it.routine.isEnabled && it.apps.isNotEmpty() }

            /** 그 날 루틴별 (확인해야 할 앱 수, 확인한 앱 수) */
            fun partsFor(day: LocalDate): List<Pair<RoutineEntity, Pair<Int, Int>>> {
                val key = DateKey.of(day)
                return active
                    .filter { it.routine.occursOn(day) }
                    .filter { !Instant.ofEpochMilli(it.routine.createdAt).atZone(zone).toLocalDate().isAfter(day) }
                    .map { rwa ->
                        val done = rwa.apps.count { Triple(rwa.routine.routineId, it.packageName, key) in checked }
                        rwa.routine to (rwa.apps.size to done)
                    }
            }

            val days = (0 until WINDOW_DAYS).map { today.minusDays(WINDOW_DAYS - 1 - it) }
            val parts = days.map { partsFor(it) }
            val dayStats = days.mapIndexed { i, d ->
                DayStat(d, parts[i].sumOf { it.second.first }, parts[i].sumOf { it.second.second })
            }
            val dayResults: List<Boolean?> = dayStats.map { if (it.hasRoutine) it.isPerfect else null }

            val last30 = dayStats.takeLast(30)
            val parts30 = parts.takeLast(30)

            val perRoutine = routines.filter { it.apps.isNotEmpty() }.map { rwa ->
                val id = rwa.routine.routineId
                var total = 0
                var done = 0
                parts30.forEach { list ->
                    list.firstOrNull { it.first.routineId == id }?.let {
                        total += it.second.first
                        done += it.second.second
                    }
                }
                // 이 루틴의 날별 결과: 예정 없던 날은 null
                val results = parts.map { list ->
                    list.firstOrNull { it.first.routineId == id }?.let { it.second.second >= it.second.first }
                }
                RoutineStat(rwa.routine, total, done, Streaks.current(results), Streaks.best(results))
            }

            fun rateOf(list: List<DayStat>): Float? {
                val t = list.sumOf { it.total }
                return if (t == 0) null else list.sumOf { it.done }.toFloat() / t
            }

            StatsSummary(
                today = dayStats.last(),
                week = dayStats.takeLast(7),
                calendar = dayStats.takeLast(35),
                perRoutine = perRoutine,
                streakDays = Streaks.current(dayResults),
                bestStreak = Streaks.best(dayResults),
                perfectDays = dayStats.count { it.isPerfect },
                monthRate = rateOf(last30) ?: 0f,
                weekRate = rateOf(dayStats.takeLast(7)),
                prevWeekRate = rateOf(dayStats.dropLast(7).takeLast(7)),
                totalChecks = checkedCount,
                hasAnyRoutine = routines.any { it.apps.isNotEmpty() }
            )
        }
    }

    private companion object {
        const val WINDOW_DAYS = 120L
    }
}
