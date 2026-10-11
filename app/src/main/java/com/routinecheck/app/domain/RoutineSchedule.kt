package com.routinecheck.app.domain

import com.routinecheck.app.util.DayMask
import com.routinecheck.app.util.TimeText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 루틴 시간 계산 (순수 로직 — 단위 테스트 대상).
 *
 * "진행 중인 루틴" 정의: 오늘 반복 요일에 해당하고, 시작 시각이 지났고, 아직 모든 앱을 확인하지 않은 루틴.
 * → 시작 시각을 놓쳐도 그날이 끝날 때까지 챗헤드 앞쪽에 남아서 "부주의로 못 본 것"을 챙겨줍니다.
 */
object RoutineSchedule {

    fun isScheduledOn(daysMask: Int, date: LocalDate): Boolean =
        DayMask.contains(daysMask, date.dayOfWeek)

    fun hasStarted(startTime: String, now: LocalTime): Boolean =
        !now.isBefore(TimeText.parse(startTime))

    /** now 이후(초과) 가장 가까운 알람 시각. 반복 요일이 하나도 없으면 null */
    fun nextTrigger(startTime: String, daysMask: Int, now: LocalDateTime): LocalDateTime? {
        if (daysMask and DayMask.ALL == 0) return null
        val time = TimeText.parse(startTime)
        for (offset in 0..7L) {
            val day = now.toLocalDate().plusDays(offset)
            if (!isScheduledOn(daysMask, day)) continue
            val candidate = day.atTime(time)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
}
