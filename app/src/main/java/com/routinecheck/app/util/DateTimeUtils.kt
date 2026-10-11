package com.routinecheck.app.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** "HH:mm" 문자열 <-> LocalTime */
object TimeText {
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parse(text: String): LocalTime =
        runCatching { LocalTime.parse(text, FORMAT) }.getOrDefault(LocalTime.of(7, 0))

    fun format(time: LocalTime): String = time.format(FORMAT)

    fun format(hour: Int, minute: Int): String = format(LocalTime.of(hour, minute))

    /** "07:00" → "오전 7:00" */
    fun display(text: String): String {
        val t = parse(text)
        val ampm = if (t.hour < 12) "오전" else "오후"
        val h = when (val h12 = t.hour % 12) { 0 -> 12; else -> h12 }
        return "%s %d:%02d".format(ampm, h, t.minute)
    }
}

/** 날짜 <-> DB 키 문자열("yyyy-MM-dd") */
object DateKey {
    fun of(date: LocalDate): String = date.toString()           // ISO_LOCAL_DATE
    fun parse(key: String): LocalDate = LocalDate.parse(key)
    fun today(): String = of(LocalDate.now())

    /** date가 속한 주의 월요일 */
    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())
}
