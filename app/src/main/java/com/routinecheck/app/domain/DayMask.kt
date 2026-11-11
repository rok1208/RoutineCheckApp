package com.routinecheck.app.domain

import java.time.DayOfWeek

/**
 * RoutineEntity.daysOfWeek 비트마스크 도우미.
 * 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64  (예: 월/수/금 = 1+4+16 = 21)
 *
 * 순수 Kotlin(java.time)만 사용하므로 단위 테스트에서 그대로 검증할 수 있습니다.
 */
object DayMask {
    const val MON = 1
    const val TUE = 2
    const val WED = 4
    const val THU = 8
    const val FRI = 16
    const val SAT = 32
    const val SUN = 64

    const val WEEKDAYS = MON or TUE or WED or THU or FRI   // 31
    const val WEEKEND = SAT or SUN                          // 96
    const val ALL = WEEKDAYS or WEEKEND                     // 127

    /** 월요일부터 일요일 순서 */
    val ORDERED: List<DayOfWeek> = DayOfWeek.values().toList()

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    fun days(mask: Int): List<DayOfWeek> = ORDERED.filter { contains(mask, it) }

    fun of(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or bit(d) }

    fun shortName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> tr2("월", "Mon")
        DayOfWeek.TUESDAY -> tr2("화", "Tue")
        DayOfWeek.WEDNESDAY -> tr2("수", "Wed")
        DayOfWeek.THURSDAY -> tr2("목", "Thu")
        DayOfWeek.FRIDAY -> tr2("금", "Fri")
        DayOfWeek.SATURDAY -> tr2("토", "Sat")
        DayOfWeek.SUNDAY -> tr2("일", "Sun")
    }

    /** 화면 표시용 요약: "매일" / "평일" / "주말" / "월·수·금" / "반복 없음" */
    fun label(mask: Int): String {
        val m = mask and ALL
        return when (m) {
            0 -> tr("반복 없음")
            ALL -> tr("매일")
            WEEKDAYS -> tr("평일")
            WEEKEND -> tr("주말")
            else -> days(m).joinToString("·") { shortName(it) }
        }
    }
}
