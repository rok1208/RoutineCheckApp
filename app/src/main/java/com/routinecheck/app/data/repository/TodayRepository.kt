package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.entity.RoutineAppEntity
import com.routinecheck.app.data.local.entity.RoutineEntity
import com.routinecheck.app.domain.ActiveRoutineInput
import com.routinecheck.app.domain.RoutineSchedule
import com.routinecheck.app.util.DateKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 오늘 예정된 루틴 하나의 진행 상태 */
data class TodayRoutine(
    val routine: RoutineEntity,
    val apps: List<RoutineAppEntity>,
    val checkedPackages: Set<String>,
    val hasStarted: Boolean
) {
    val total: Int get() = apps.size
    val done: Int get() = apps.count { it.packageName in checkedPackages }
    val isComplete: Boolean get() = apps.isNotEmpty() && done == total
    /** 시작 시각이 지났고 아직 다 확인하지 않은 루틴 = 챗헤드 앞쪽에 올라오는 루틴 */
    val isActive: Boolean get() = hasStarted && !isComplete && apps.isNotEmpty()

    fun toActiveInput() = ActiveRoutineInput(
        routineId = routine.routineId,
        name = routine.name,
        startTime = routine.startTime,
        apps = apps.map { it.packageName to it.appLabel },
        checkedPackages = checkedPackages
    )
}

data class TodaySnapshot(
    val now: LocalDateTime,
    val routines: List<TodayRoutine>,
    /** 이 스냅샷이 보여주는 날짜. 기본은 오늘 (홈에서 다른 요일을 고르면 그 날짜) */
    val day: LocalDate = now.toLocalDate()
) {
    val date: LocalDate get() = day
    val dateKey: String get() = DateKey.of(day)
    val isToday: Boolean get() = day == now.toLocalDate()
    val isPast: Boolean get() = day.isBefore(now.toLocalDate())
    val isFuture: Boolean get() = day.isAfter(now.toLocalDate())
    val active: List<TodayRoutine> get() = routines.filter { it.isActive }
}

/**
 * "오늘" 기준 루틴 진행 상황을 1분마다 갱신해서 내보냅니다.
 * 홈 화면(오늘의 루틴)과 챗헤드 서비스가 같은 데이터를 봅니다.
 */
@Singleton
class TodayRepository @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(): Flow<TodaySnapshot> {
        val clock = minuteTicker()
        val checks = clock.map { it.toLocalDate() }
            .distinctUntilChanged()
            .flatMapLatest { date -> routineRepository.observeChecklistForDate(DateKey.of(date)) }

        return combine(
            clock,
            routineRepository.observeRoutinesWithApps(),
            checks
        ) { now, routinesWithApps, states ->
            val date = now.toLocalDate()
            val dateKey = DateKey.of(date)
            val checkedByRoutine = states
                .filter { it.isChecked && it.date == dateKey }
                .groupBy({ it.routineId }, { it.packageName })

            val today = routinesWithApps
                .filter { it.routine.isEnabled && RoutineSchedule.isScheduledOn(it.routine.daysOfWeek, date) }
                .map { rwa ->
                    TodayRoutine(
                        routine = rwa.routine,
                        apps = rwa.apps,
                        checkedPackages = checkedByRoutine[rwa.routine.routineId].orEmpty().toSet(),
                        hasStarted = RoutineSchedule.hasStarted(rwa.routine.startTime, now.toLocalTime())
                    )
                }
                .sortedBy { it.routine.startTime }
            TodaySnapshot(now, today)
        }
    }

    /**
     * 특정 날짜의 루틴 진행 상황 (홈의 요일 선택용).
     * 지난 날짜는 모든 루틴을 "시작됨"으로, 앞으로의 날짜는 "예정"으로 봅니다.
     */
    fun observeDay(day: LocalDate): Flow<TodaySnapshot> = combine(
        minuteTicker(),
        routineRepository.observeRoutinesWithApps(),
        routineRepository.observeChecklistForDate(DateKey.of(day))
    ) { now, routinesWithApps, states ->
        val key = DateKey.of(day)
        val checkedByRoutine = states
            .filter { it.isChecked && it.date == key }
            .groupBy({ it.routineId }, { it.packageName })
        val today = now.toLocalDate()
        val routines = routinesWithApps
            .filter { it.routine.isEnabled && RoutineSchedule.isScheduledOn(it.routine.daysOfWeek, day) }
            .map { rwa ->
                TodayRoutine(
                    routine = rwa.routine,
                    apps = rwa.apps,
                    checkedPackages = checkedByRoutine[rwa.routine.routineId].orEmpty().toSet(),
                    hasStarted = when {
                        day.isBefore(today) -> true
                        day.isAfter(today) -> false
                        else -> RoutineSchedule.hasStarted(rwa.routine.startTime, now.toLocalTime())
                    }
                )
            }
            .sortedBy { it.routine.startTime }
        TodaySnapshot(now, routines, day)
    }

    private fun minuteTicker(): Flow<LocalDateTime> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(ChronoUnit.MILLIS.between(now, nextMinute).coerceAtLeast(1_000))
        }
    }
}
