package com.routinecheck.app

import com.routinecheck.app.domain.AchievementBadge
import com.routinecheck.app.domain.AchievementMetrics
import com.routinecheck.app.domain.CheerMessages
import com.routinecheck.app.domain.RoutineLevel
import com.routinecheck.app.domain.Streaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.6.0 통계 성취 요소 (연속 기록 · 레벨 · 배지 · 응원 문구) */
class AchievementsTest {

    @Test
    fun streak_skipsDaysWithoutRoutine() {
        // 완료, 루틴 없음, 완료, 완료 → 3일 연속 (루틴 없는 날은 끊지 않음)
        assertEquals(3, Streaks.current(listOf(true, null, true, true)))
    }

    @Test
    fun streak_todayInProgressDoesNotBreak() {
        // 오늘(마지막)이 아직 미완료면 어제까지의 연속을 유지
        assertEquals(2, Streaks.current(listOf(false, true, true, false)))
    }

    @Test
    fun streak_brokenByMissedDay() {
        assertEquals(1, Streaks.current(listOf(true, true, false, true)))
        assertEquals(0, Streaks.current(listOf(true, false, false)))
        assertEquals(0, Streaks.current(emptyList()))
    }

    @Test
    fun bestStreak_findsLongestRun() {
        assertEquals(3, Streaks.best(listOf(true, true, false, true, null, true, true, false)))
        assertEquals(0, Streaks.best(listOf(null, false)))
    }

    @Test
    fun level_andProgress() {
        assertEquals(RoutineLevel.SPROUT, RoutineLevel.of(0))
        assertEquals(RoutineLevel.LEAF, RoutineLevel.of(20))
        assertEquals(RoutineLevel.MASTER, RoutineLevel.of(10_000))
        assertEquals(0.5f, RoutineLevel.progress(10), 0.001f)   // 0 → 20 의 절반
        assertEquals(1f, RoutineLevel.progress(600), 0.001f)
    }

    @Test
    fun badges_metByMetrics() {
        val m = AchievementMetrics(checks = 1, perfectDays = 0, bestStreak = 3)
        assertTrue(AchievementBadge.FIRST_CHECK.isMet(m))
        assertTrue(AchievementBadge.STREAK_3.isMet(m))
        assertFalse(AchievementBadge.STREAK_7.isMet(m))
        assertEquals(3f / 7f, AchievementBadge.STREAK_7.progress(m), 0.001f)
    }

    @Test
    fun cheer_changesWithProgress() {
        assertEquals("첫 루틴을 만들어 볼까요?", CheerMessages.of(0, 0, 0, 0, hasAnyRoutine = false).headline)
        assertEquals("오늘 루틴을 모두 끝냈어요! 🎉", CheerMessages.of(3, 3, 4, 4, true).headline)
        assertEquals("거의 다 왔어요! 1개만 더", CheerMessages.of(3, 2, 2, 5, true).headline)
        assertEquals("오늘 끝내면 연속 3일이 돼요 🔥", CheerMessages.of(3, 2, 2, 5, true).detail)
        assertEquals("오늘 끝내면 연속 6일로 최고 기록을 넘어요 🔥", CheerMessages.of(4, 0, 5, 5, true).detail)
    }
}
