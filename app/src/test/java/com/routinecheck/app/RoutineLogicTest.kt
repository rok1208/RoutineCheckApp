package com.routinecheck.app

import com.routinecheck.app.domain.ActiveRoutineInput
import com.routinecheck.app.domain.ChatHeadKind
import com.routinecheck.app.domain.ChatHeadOrdering
import com.routinecheck.app.domain.DateKey
import com.routinecheck.app.domain.DayMask
import com.routinecheck.app.domain.LinkUtils
import com.routinecheck.app.domain.PinnedItem
import com.routinecheck.app.domain.RoutineSchedule
import com.routinecheck.app.domain.TimeText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun schedule_firesOnceAtDateTime() {
        // 일정 2026-10-15 15:30: 그 전이면 그 시각, 지나면 다시 울리지 않음 (요일 마스크는 무시)
        val before = LocalDateTime.of(2026, 10, 12, 9, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 15, 15, 30),
            RoutineSchedule.nextTrigger("15:30", 0, before, scheduleDate = "2026-10-15")
        )
        val after = LocalDateTime.of(2026, 10, 15, 15, 30)
        assertNull(RoutineSchedule.nextTrigger("15:30", DayMask.ALL, after, scheduleDate = "2026-10-15"))
        assertNull(RoutineSchedule.nextTrigger("15:30", 0, before, scheduleDate = "잘못된 날짜"))
    }

    @Test
    fun schedule_occursOnlyOnItsDate() {
        val day = LocalDate.of(2026, 10, 15) // 목
        assertTrue(RoutineSchedule.occursOn(0, "2026-10-15", day))
        assertFalse(RoutineSchedule.occursOn(DayMask.ALL, "2026-10-15", day.plusDays(7)))
        // 반복 루틴은 기존 요일 판정 그대로
        assertTrue(RoutineSchedule.occursOn(DayMask.WEEKDAYS, null, day))
        assertFalse(RoutineSchedule.occursOn(DayMask.WEEKEND, null, day))
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
        val pinned = listOf(app(10, "maps", 0), app(11, "kakao", 1), app(12, "maps", 2))
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
        val result = ChatHeadOrdering.build(listOf(done), listOf(app(1, "p", 0)))
        assertEquals(listOf("p"), result.map { it.packageName })
    }

    // ---------------- v5: 폴더 · 웹사이트 ----------------

    private fun app(id: Long, pkg: String, order: Int, parent: Long? = null) =
        PinnedItem(id, ChatHeadKind.APP, pkg.uppercase(), packageName = pkg, parentId = parent, sortOrder = order)
    private fun folder(id: Long, title: String, order: Int, parent: Long? = null) =
        PinnedItem(id, ChatHeadKind.FOLDER, title, parentId = parent, sortOrder = order)
    private fun link(id: Long, url: String, order: Int, parent: Long? = null) =
        PinnedItem(id, ChatHeadKind.LINK, url, url = url, parentId = parent, sortOrder = order)

    private val tree = listOf(
        app(1, "maps", 0),
        folder(2, "업무", 1),
        link(3, "https://naver.com", 2),
        app(4, "slack", 0, parent = 2),
        folder(5, "개발", 1, parent = 2),
        app(6, "github", 0, parent = 5),
        link(7, "https://docs.dev", 1, parent = 5)
    )

    @Test
    fun ordering_rootShowsRoutineThenFoldersThenItems() {
        val r = ActiveRoutineInput(1, "출근", "08:00", listOf("slack" to "Slack"), emptySet())
        val result = ChatHeadOrdering.build(listOf(r), tree)
        assertEquals(listOf("routine:slack", "item:2", "item:1", "item:3"), result.map { it.key })
        val work = result.first { it.kind == ChatHeadKind.FOLDER }
        assertEquals(2, work.childCount)                              // slack, 개발
        assertEquals(setOf("slack", "github"), work.folderPackages.toSet()) // 하위 폴더 앱까지
    }

    @Test
    fun ordering_insideFolderShowsOnlyThatLevel() {
        val r = ActiveRoutineInput(1, "출근", "08:00", listOf("x" to "X"), emptySet())
        val result = ChatHeadOrdering.build(listOf(r), tree, folderId = 2)
        assertEquals(listOf("item:5", "item:4"), result.map { it.key }) // 루틴 앱은 섞지 않음, 폴더 먼저
        val dev = ChatHeadOrdering.build(emptyList(), tree, folderId = 5)
        assertEquals(listOf(ChatHeadKind.APP, ChatHeadKind.LINK), dev.map { it.kind })
    }

    @Test
    fun folder_pathAndDescendants() {
        assertEquals(listOf("업무", "개발"), ChatHeadOrdering.pathTo(tree, 5).map { it.title })
        assertTrue(ChatHeadOrdering.pathTo(tree, null).isEmpty())
        assertTrue(ChatHeadOrdering.pathTo(tree, 99).isEmpty())
        assertEquals(setOf(4L, 5L, 6L, 7L), ChatHeadOrdering.descendants(tree, 2).map { it.id }.toSet())
        // 순환 참조가 있어도 멈춤
        val loop = listOf(folder(1, "a", 0, parent = 2), folder(2, "b", 0, parent = 1))
        assertEquals(setOf(1L, 2L), ChatHeadOrdering.descendants(loop, 1).map { it.id }.toSet())
    }

    @Test
    fun link_normalize() {
        assertEquals("https://naver.com", LinkUtils.normalize("  naver.com "))
        assertEquals("http://a.com/x?y=1", LinkUtils.normalize("http://a.com/x?y=1"))
        assertNull(LinkUtils.normalize("네이버"))
        assertNull(LinkUtils.normalize("a b.com"))
        assertNull(LinkUtils.normalize(""))
        assertEquals("m.naver.com", LinkUtils.hostOf("https://m.naver.com/news?x=1"))
        assertEquals("naver.com", LinkUtils.defaultTitle("https://www.naver.com/"))
    }
}
