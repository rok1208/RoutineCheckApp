package com.routinecheck.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 루틴 시간 계산 (순수 로직 — 단위 테스트 대상).
 *
 * "진행 중인 루틴" 정의: 오늘 실행되는 날이고, 시작 시각이 지났고, 아직 모든 앱을 확인하지 않은 루틴.
 * → 시작 시각을 놓쳐도 그날이 끝날 때까지 챗헤드 앞쪽에 남아서 "부주의로 못 본 것"을 챙겨줍니다.
 *
 * v0.5.0부터 루틴은 두 종류입니다.
 *  - 요일 반복 루틴: date == null, daysOfWeek 비트마스크의 요일마다
 *  - 일정: date == "yyyy-MM-dd", 그 날짜 그 시각에 한 번만 (daysOfWeek 는 무시)
 */
object RoutineSchedule {

    fun isScheduledOn(daysMask: Int, date: LocalDate): Boolean =
        DayMask.contains(daysMask, date.dayOfWeek)

    /** 이 루틴/일정이 day 에 실행되는지. scheduleDate 가 있으면 그 날짜에만 */
    fun occursOn(daysMask: Int, scheduleDate: String?, day: LocalDate): Boolean {
        val oneTime = scheduleDate?.let { runCatching { DateKey.parse(it) }.getOrNull() }
        return when {
            scheduleDate != null -> oneTime == day
            else -> isScheduledOn(daysMask, day)
        }
    }

    fun hasStarted(startTime: String, now: LocalTime): Boolean =
        !now.isBefore(TimeText.parse(startTime))

    /**
     * now 이후(초과) 가장 가까운 알람 시각.
     *  - 일정(scheduleDate 있음): 그 날짜·시각이 아직 안 지났으면 그 시각, 지났으면 null (다시 울리지 않음)
     *  - 반복 루틴: 반복 요일이 하나도 없으면 null
     */
    fun nextTrigger(startTime: String, daysMask: Int, now: LocalDateTime, scheduleDate: String? = null): LocalDateTime? {
        val time = TimeText.parse(startTime)
        if (scheduleDate != null) {
            val day = runCatching { DateKey.parse(scheduleDate) }.getOrNull() ?: return null
            val at = day.atTime(time)
            return if (at.isAfter(now)) at else null
        }
        if (daysMask and DayMask.ALL == 0) return null
        for (offset in 0..7L) {
            val day = now.toLocalDate().plusDays(offset)
            if (!isScheduledOn(daysMask, day)) continue
            val candidate = day.atTime(time)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
}
