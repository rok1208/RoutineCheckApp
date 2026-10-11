package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.entity.RoutineEntity
import com.routinecheck.app.domain.RoutineSchedule
import com.routinecheck.app.util.DateKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class DayStat(val date: LocalDate, val total: Int, val done: Int) {
    val rate: Float get() = if (total == 0) 0f else done.toFloat() / total
    val hasRoutine: Boolean get() = total > 0
}

data class RoutineStat(val routine: RoutineEntity, val total: Int, val done: Int) {
    val rate: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class StatsSummary(
    /** 최근 7일 (오래된 날 → 오늘) */
    val week: List<DayStat>,
    /** 최근 30일 루틴별 완료율 */
    val perRoutine: List<RoutineStat>,
    /** 오늘부터 거꾸로 셌을 때, 예정된 루틴을 100% 완료한 연속 일수 (오늘이 미완료면 어제부터) */
    val streakDays: Int,
    val monthRate: Float
)

/**
 * 완료율 통계. 체크 기록(routine_checklist_state)과 현재 루틴 구성을 바탕으로 계산합니다.
 * (과거에 루틴의 앱 구성이 바뀐 기록은 저장하지 않으므로 "현재 등록된 앱" 기준의 근사치입니다.)
 */
@Singleton
class StatsRepository @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    fun observeSummary(today: LocalDate = LocalDate.now()): Flow<StatsSummary> {
        val from = today.minusDays(29)
        return combine(
            routineRepository.observeRoutinesWithApps(),
            routineRepository.observeChecklistBetween(DateKey.of(from), DateKey.of(today))
        ) { routines, states ->
            val checked = states.filter { it.isChecked }
                .map { Triple(it.routineId, it.packageName, it.date) }
                .toHashSet()
            val zone = ZoneId.systemDefault()

            fun statFor(day: LocalDate): Pair<DayStat, List<Pair<RoutineEntity, Pair<Int, Int>>>> {
                val key = DateKey.of(day)
                val parts = routines
                    .filter { it.routine.isEnabled && it.apps.isNotEmpty() }
                    .filter { RoutineSchedule.isScheduledOn(it.routine.daysOfWeek, day) }
                    .filter { !Instant.ofEpochMilli(it.routine.createdAt).atZone(zone).toLocalDate().isAfter(day) }
                    .map { rwa ->
                        val done = rwa.apps.count { Triple(rwa.routine.routineId, it.packageName, key) in checked }
                        rwa.routine to (rwa.apps.size to done)
                    }
                return DayStat(day, parts.sumOf { it.second.first }, parts.sumOf { it.second.second }) to parts
            }

            val days = (0L..29L).map { today.minusDays(29 - it) }
            val perDay = days.map { statFor(it) }

            val week = perDay.takeLast(7).map { it.first }

            val perRoutine = routines.filter { it.apps.isNotEmpty() }.map { rwa ->
                var total = 0
                var done = 0
                perDay.forEach { (_, parts) ->
                    parts.firstOrNull { it.first.routineId == rwa.routine.routineId }?.let {
                        total += it.second.first
                        done += it.second.second
                    }
                }
                RoutineStat(rwa.routine, total, done)
            }

            var streak = 0
            val reversed = perDay.map { it.first }.reversed()
            val startIndex = if (reversed.first().let { it.hasRoutine && it.done < it.total }) 1 else 0
            for (i in startIndex until reversed.size) {
                val d = reversed[i]
                if (!d.hasRoutine) continue          // 루틴 없는 날은 건너뜀 (연속 끊지 않음)
                if (d.done == d.total) streak++ else break
            }

            val monthTotal = perDay.sumOf { it.first.total }
            val monthDone = perDay.sumOf { it.first.done }
            StatsSummary(
                week = week,
                perRoutine = perRoutine,
                streakDays = streak,
                monthRate = if (monthTotal == 0) 0f else monthDone.toFloat() / monthTotal
            )
        }
    }
}
