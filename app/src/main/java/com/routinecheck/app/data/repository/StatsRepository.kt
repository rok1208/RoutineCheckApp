package com.routinecheck.app.data.repository

import android.content.Context
import com.routinecheck.app.data.local.RoutineEntity
import com.routinecheck.app.data.local.occursOn
import com.routinecheck.app.domain.DateKey
import com.routinecheck.app.domain.RoutineSchedule
import com.routinecheck.app.domain.StatsPeriod
import com.routinecheck.app.domain.Streaks
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    /** 이 루틴을 모두 끝낸 날이 이어진 횟수 (현재 / 최고) */
    val streak: Int,
    val bestStreak: Int,
    /** 시작 이후 이 루틴이 예정됐던 날 수 */
    val scheduledDays: Int
)

data class StatsSummary(
    /** 통계 시작일 = 앱 설치일 (최대 1년 전까지) */
    val startDate: LocalDate,
    /** 시작한 지 며칠째 (설치한 날 = 1) */
    val dayNumber: Int,
    /** 오늘 예정된 전체 루틴 기준 (아직 시각이 안 된 루틴 포함) */
    val today: DayStat,
    /**
     * 시작일 → 오늘의 날별 기록. 평균·연속 기록 계산용이라
     * 오늘은 "시각이 지났거나 이미 끝낸" 루틴만 셉니다 (아직 시간이 안 된 루틴 때문에 낮아지지 않게).
     */
    val days: List<DayStat>,
    val perRoutine: List<RoutineStat>,
    /** 예정된 루틴을 100% 끝낸 날의 현재 연속 일수 (오늘이 아직 미완료면 어제부터) */
    val streakDays: Int,
    /** 시작 이후 가장 길었던 연속 일수 */
    val bestStreak: Int,
    /** 시작 이후 루틴을 모두 끝낸 날 수 */
    val perfectDays: Int,
    /** 시작 이후 전체 완료율 (기록이 없으면 null) */
    val overallRate: Float?,
    /** 지금까지 확인한 앱 수 */
    val totalChecks: Int,
    /** 앱이 등록된 루틴이 하나라도 있는지 */
    val hasAnyRoutine: Boolean
) {
    /** 그 날의 기록 (시작 전·미래면 null) */
    fun dayOf(date: LocalDate): DayStat? = days.firstOrNull { it.date == date }

    /** 기간 완료율 (기록이 없으면 null) */
    fun rateBetween(from: LocalDate, to: LocalDate): Float? {
        val list = days.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val t = list.sumOf { it.total }
        return if (t == 0) null else list.sumOf { it.done }.toFloat() / t
    }
}

/**
 * 완료율 · 연속 기록 통계 (v0.6.0).
 *
 * 모든 통계는 **앱을 설치한 날부터** 계산합니다. 오늘 설치해 오늘 루틴을 모두 끝냈다면
 * 이번 주·시작 이후 완료율은 100%여야 하므로, 설치 전 날짜는 아예 기간에 넣지 않습니다.
 * 루틴을 만들기 전 날짜도 그 루틴은 세지 않고, 오늘은 시각이 아직 안 된 루틴을 빼고 셉니다.
 * (과거에 루틴의 앱 구성이 바뀐 기록은 저장하지 않으므로 "현재 등록된 앱" 기준의 근사치입니다.)
 */
@Singleton
class StatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routineRepository: RoutineRepository
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** 앱 설치일 (PackageManager 의 최초 설치 시각) */
    private val installDate: LocalDate by lazy {
        val millis = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(System.currentTimeMillis())
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    }

    fun observeSummary(today: LocalDate = LocalDate.now()): Flow<StatsSummary> {
        val start = StatsPeriod.startDate(installDate, today, MAX_DAYS)
        return combine(
            routineRepository.observeRoutinesWithApps(),
            routineRepository.observeChecklistBetween(DateKey.of(start), DateKey.of(today)),
            routineRepository.observeCheckedCount()
        ) { routines, states, checkedCount ->
            val checked = states.filter { it.isChecked }
                .map { Triple(it.routineId, it.packageName, it.date) }
                .toHashSet()
            val active = routines.filter { it.routine.isEnabled && it.apps.isNotEmpty() }
            val now = LocalTime.now()

            /** 그 날 루틴별 (확인해야 할 앱 수, 확인한 앱 수). onlyStarted = 오늘 아직 시각이 안 된 루틴 제외 */
            fun partsFor(day: LocalDate, onlyStarted: Boolean): List<Pair<RoutineEntity, Pair<Int, Int>>> {
                val key = DateKey.of(day)
                return active
                    .filter { it.routine.occursOn(day) }
                    .filter { !Instant.ofEpochMilli(it.routine.createdAt).atZone(zone).toLocalDate().isAfter(day) }
                    .map { rwa ->
                        val done = rwa.apps.count { Triple(rwa.routine.routineId, it.packageName, key) in checked }
                        rwa.routine to (rwa.apps.size to done)
                    }
                    .filter { (routine, counts) ->
                        !onlyStarted || counts.second >= counts.first || RoutineSchedule.hasStarted(routine.startTime, now)
                    }
            }

            fun statOf(day: LocalDate, parts: List<Pair<RoutineEntity, Pair<Int, Int>>>) =
                DayStat(day, parts.sumOf { it.second.first }, parts.sumOf { it.second.second })

            val count = StatsPeriod.dayNumber(start, today)
            val dates = (0 until count).map { start.plusDays(it.toLong()) }
            val parts = dates.map { partsFor(it, onlyStarted = it == today) }
            val days = dates.mapIndexed { i, d -> statOf(d, parts[i]) }
            val dayResults: List<Boolean?> = days.map { if (it.hasRoutine) it.isPerfect else null }

            val perRoutine = routines.filter { it.apps.isNotEmpty() }.map { rwa ->
                val id = rwa.routine.routineId
                val results = parts.map { list ->
                    list.firstOrNull { it.first.routineId == id }?.let { it.second.second >= it.second.first }
                }
                RoutineStat(rwa.routine, Streaks.current(results), Streaks.best(results), results.count { it != null })
            }

            val total = days.sumOf { it.total }
            StatsSummary(
                startDate = start,
                dayNumber = count,
                today = statOf(today, partsFor(today, onlyStarted = false)),
                days = days,
                perRoutine = perRoutine,
                streakDays = Streaks.current(dayResults),
                bestStreak = Streaks.best(dayResults),
                perfectDays = days.count { it.isPerfect },
                overallRate = if (total == 0) null else days.sumOf { it.done }.toFloat() / total,
                totalChecks = checkedCount,
                hasAnyRoutine = routines.any { it.apps.isNotEmpty() }
            )
        }
    }

    private companion object {
        /** 한 번에 계산하는 최대 기간 (1년) */
        const val MAX_DAYS = 365L
    }
}
