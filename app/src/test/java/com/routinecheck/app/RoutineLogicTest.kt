package com.routinecheck.app

import com.routinecheck.app.domain.ActiveRoutineInput
import com.routinecheck.app.domain.ChatHeadOrdering
import com.routinecheck.app.domain.RoutineSchedule
import com.routinecheck.app.util.DateKey
import com.routinecheck.app.util.DayMask
import com.routinecheck.app.util.TimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 기기 없이 돌아가는 순수 로직 테스트 (Android Studio: 우클릭 → Run 'RoutineLogicTest') */
class RoutineLogicTest {

    @Test
    fun dayMask_encodesAndLabels() {
        assertEquals(21, DayMask.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), DayMask.days(21))
        assertTrue(DayMask.contains(21, DayOfWeek.WEDNESDAY))
        assertFalse(DayMask.contains(21, DayOfWeek.TUESDAY))
        assertEquals("월·수·금", DayMask.label(21))
        assertEquals("평일", DayMask.label(31))
        assertEquals("주말", DayMask.label(96))
        assertEquals("매일", DayMask.label(127))
        assertEquals(21 or 2, DayMask.toggle(21, DayOfWeek.TUESDAY))
    }

    @Test
    fun timeText_roundTrip() {
        assertEquals(LocalTime.of(7, 5), TimeText.parse("07:05"))
        assertEquals("07:05", TimeText.format(7, 5))
        assertEquals("오전 7:05", TimeText.display("07:05"))
        assertEquals("오후 12:00", TimeText.display("12:00"))
        assertEquals("오전 12:30", TimeText.display("00:30"))
        assertEquals("오후 11:59", TimeText.display("23:59"))
    }

    @Test
    fun dateKey_weekStartIsMonday() {
        val friday = LocalDate.of(2026, 10, 9)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 5), DateKey.weekStart(friday))
        assertEquals("2026-10-09", DateKey.of(friday))
    }

    @Test
    fun nextTrigger_sameDayLaterTime() {
        // 2026-10-09 (금) 06:00, 평일 07:00 루틴 → 오늘 07:00
        val now = LocalDateTime.of(2026, 10, 9, 6, 0)
        assertEquals(LocalDateTime.of(2026, 10, 9, 7, 0), RoutineSchedule.nextTrigger("07:00", DayMask.WEEKDAYS, now))
    }

    @Test
    fun nextTrigger_skipsWeekendAndPassedTime() {
        // 금 07:00 정각(이미 지남으로 처리) → 다음 월요일 07:00
        val now = LocalDateTime.of(2026, 10, 9, 7, 0)
        assertEquals(LocalDateTime.of(2026, 10, 12, 7, 0), RoutineSchedule.nextTrigger("07:00", DayMask.WEEKDAYS, now))
    }

    @Test
    fun nextTrigger_singleDayOneWeekLater() {
        // 금요일만, 금 08:00 → 다음 주 금 07:00
        val now = LocalDateTime.of(2026, 10, 9, 8, 0)
        assertEquals(LocalDateTime.of(2026, 10, 16, 7, 0), RoutineSchedule.nextTrigger("07:00", DayMask.FRI, now))
        assertNull(RoutineSchedule.nextTrigger("07:00", 0, now))
    }

    @Test
    fun hasStarted_boundary() {
        assertTrue(RoutineSchedule.hasStarted("07:00", LocalTime.of(7, 0)))
        assertFalse(RoutineSchedule.hasStarted("07:00", LocalTime.of(6, 59)))
    }

    @Test
    fun ordering_routineFirstUncheckedThenCheckedThenPinned() {
        val morning = ActiveRoutineInput(
            routineId = 1, name = "기상", startTime = "07:00",
            apps = listOf("cal" to "캘린더", "weather" to "날씨", "kakao" to "카카오톡"),
            checkedPackages = setOf("cal")
        )
        val pinned = listOf("maps" to "지도", "kakao" to "카카오톡", "maps" to "지도")
        val result = ChatHeadOrdering.build(listOf(morning), pinned)
        assertEquals(listOf("weather", "kakao", "cal", "maps"), result.map { it.packageName })
        assertEquals(listOf(false, false, true, null), result.map { it.isChecked })
        assertTrue(result.first().isRoutineApp)
        assertFalse(result.last().isRoutineApp)
    }

    @Test
    fun ordering_sharedAppAcrossRoutines() {
        val a = ActiveRoutineInput(1, "A", "07:00", listOf("x" to "X", "y" to "Y"), setOf("x"))
        val b = ActiveRoutineInput(2, "B", "08:00", listOf("x" to "X"), emptySet())
        val result = ChatHeadOrdering.build(listOf(b, a), emptyList())
        val x = result.first { it.packageName == "x" }
        assertEquals(listOf(1L, 2L), x.routineIds)        // 시작 시각 순으로 묶임
        assertEquals(false, x.isChecked)                  // B에서는 아직 확인 전
        assertEquals("A", x.routineName)
    }

    @Test
    fun ordering_completedRoutineIsNotPrioritized() {
        val done = ActiveRoutineInput(1, "끝남", "07:00", listOf("x" to "X"), setOf("x"))
        val result = ChatHeadOrdering.build(listOf(done), listOf("p" to "P"))
        assertEquals(listOf("p"), result.map { it.packageName })
    }
}
