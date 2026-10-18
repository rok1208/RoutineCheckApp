package com.routinecheck.app.domain

import java.time.LocalDate

/**
 * 통계 화면의 성취감 요소 (v0.6.0) — 순수 로직, 단위 테스트 대상.
 *
 *  - 연속 기록: 예정된 루틴을 100% 끝낸 날이 며칠 이어졌는지 (루틴이 없는 날은 건너뜀)
 *  - 레벨: 지금까지 확인한 앱 수로 올라가는 단계
 *  - 배지: 연속 기록 · 완벽한 날 · 확인 횟수 목표를 넘으면 얻음
 *  - 응원 문구: 오늘의 진행 상황과 연속 기록에 맞춰 바뀌는 한마디
 */
object Streaks {

    /**
     * 현재 연속 일수.
     * @param results 오래된 날 → 오늘 순서. null = 예정 없음(건너뜀), true = 100% 완료, false = 미완료
     * 오늘(마지막)이 아직 미완료면 "진행 중"으로 보고 끊지 않고 어제부터 셉니다.
     */
    fun current(results: List<Boolean?>): Int {
        if (results.isEmpty()) return 0
        var i = results.lastIndex
        if (results[i] == false) i-- // 오늘은 아직 진행 중
        var streak = 0
        while (i >= 0) {
            when (results[i]) {
                true -> streak++
                false -> return streak
                null -> Unit
            }
            i--
        }
        return streak
    }

    /** 기간 안에서 가장 길었던 연속 일수 */
    fun best(results: List<Boolean?>): Int {
        var best = 0
        var run = 0
        results.forEach {
            when (it) {
                true -> { run++; if (run > best) best = run }
                false -> run = 0
                null -> Unit
            }
        }
        return best
    }
}

/** 확인한 앱 수로 올라가는 레벨 */
enum class RoutineLevel(val emoji: String, val title: String, val minChecks: Int) {
    SPROUT("🌱", "루틴 새싹", 0),
    LEAF("🌿", "꾸준한 새잎", 20),
    TREE("🌳", "습관 나무", 80),
    FOREST("🏕️", "루틴 숲", 200),
    MASTER("🏆", "루틴 마스터", 500);

    val next: RoutineLevel? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(checks: Int): RoutineLevel = entries.last { checks >= it.minChecks }

        /** 다음 레벨까지 진행률 0~1 (마지막 레벨이면 1) */
        fun progress(checks: Int): Float {
            val level = of(checks)
            val next = level.next ?: return 1f
            return ((checks - level.minChecks).toFloat() / (next.minChecks - level.minChecks)).coerceIn(0f, 1f)
        }
    }
}

/** 배지를 얻는 기준이 되는 값 */
enum class BadgeMetric { CHECKS, PERFECT_DAYS, BEST_STREAK }

data class AchievementMetrics(
    /** 지금까지 확인한 앱 수 */
    val checks: Int,
    /** 하루 루틴을 모두 끝낸 날 수 */
    val perfectDays: Int,
    /** 가장 길었던 연속 일수 */
    val bestStreak: Int
) {
    fun valueOf(metric: BadgeMetric): Int = when (metric) {
        BadgeMetric.CHECKS -> checks
        BadgeMetric.PERFECT_DAYS -> perfectDays
        BadgeMetric.BEST_STREAK -> bestStreak
    }
}

enum class AchievementBadge(
    val emoji: String,
    val title: String,
    val description: String,
    val metric: BadgeMetric,
    val target: Int
) {
    FIRST_CHECK("🎯", "첫 체크", "루틴 앱을 처음 확인했어요", BadgeMetric.CHECKS, 1),
    FIRST_PERFECT("✨", "완벽한 하루", "하루 루틴을 처음으로 모두 끝냈어요", BadgeMetric.PERFECT_DAYS, 1),
    STREAK_3("🔥", "3일 연속", "3일 연속으로 루틴을 모두 끝냈어요", BadgeMetric.BEST_STREAK, 3),
    STREAK_7("⚡", "일주일 연속", "7일 연속으로 루틴을 모두 끝냈어요", BadgeMetric.BEST_STREAK, 7),
    PERFECT_10("🌟", "완벽한 날 10일", "루틴을 모두 끝낸 날이 10일이 됐어요", BadgeMetric.PERFECT_DAYS, 10),
    STREAK_14("💪", "2주 연속", "14일 연속으로 루틴을 모두 끝냈어요", BadgeMetric.BEST_STREAK, 14),
    CHECKS_100("💯", "확인 100번", "루틴 앱을 100번 확인했어요", BadgeMetric.CHECKS, 100),
    STREAK_30("👑", "한 달 연속", "30일 연속으로 루틴을 모두 끝냈어요", BadgeMetric.BEST_STREAK, 30);

    fun isMet(m: AchievementMetrics): Boolean = m.valueOf(metric) >= target

    fun progress(m: AchievementMetrics): Float = (m.valueOf(metric).toFloat() / target).coerceIn(0f, 1f)
}

/** 통계 맨 위에 보여줄 한마디 */
data class Cheer(val headline: String, val detail: String)

object CheerMessages {

    /**
     * @param todayTotal 오늘 확인해야 할 앱 수 (0 = 오늘 루틴 없음)
     * @param todayDone 오늘 확인한 앱 수
     * @param streak 현재 연속 일수 (오늘을 다 끝냈으면 오늘 포함)
     * @param best 최고 연속 일수
     * @param hasAnyRoutine 앱이 등록된 루틴이 하나라도 있는지
     */
    fun of(todayTotal: Int, todayDone: Int, streak: Int, best: Int, hasAnyRoutine: Boolean): Cheer {
        if (!hasAnyRoutine) {
            return Cheer("첫 루틴을 만들어 볼까요?", "루틴에 앱을 등록하고 챗헤드에서 열면 여기에 기록이 쌓여요.")
        }
        if (todayTotal == 0) {
            return Cheer(
                "오늘은 쉬는 날이에요 ☕",
                if (streak > 0) "연속 ${streak}일 기록은 그대로 이어져요." else "다음 루틴에서 새 기록을 시작해 봐요."
            )
        }
        val remaining = todayTotal - todayDone
        if (remaining <= 0) {
            val detail = when {
                streak >= 3 && streak >= best -> "${streak}일 연속, 최고 기록을 쓰는 중이에요!"
                streak >= 2 -> "${streak}일 연속으로 루틴을 모두 끝냈어요."
                else -> "이 기세로 내일도 이어가 봐요."
            }
            return Cheer("오늘 루틴을 모두 끝냈어요! 🎉", detail)
        }
        val headline = when {
            todayDone == 0 -> "오늘 루틴, 시작해 볼까요?"
            todayDone * 2 >= todayTotal -> "거의 다 왔어요! ${remaining}개만 더"
            else -> "좋은 출발이에요, ${remaining}개 남았어요"
        }
        val detail = when {
            streak > 0 && streak + 1 > best -> "오늘 끝내면 연속 ${streak + 1}일로 최고 기록을 넘어요 🔥"
            streak > 0 -> "오늘 끝내면 연속 ${streak + 1}일이 돼요 🔥"
            else -> "오늘 모두 끝내면 연속 기록이 시작돼요."
        }
        return Cheer(headline, detail)
    }

    /** 루틴별 완료율에 붙이는 짧은 평가 */
    fun forRate(rate: Float, total: Int): String = when {
        total == 0 -> "아직 기록이 없어요"
        rate >= 1f -> "완벽해요"
        rate >= 0.8f -> "아주 잘하고 있어요"
        rate >= 0.5f -> "절반 넘게 지켰어요"
        rate > 0f -> "조금씩 늘려 봐요"
        else -> "오늘부터 시작해 봐요"
    }
}

/** 통계 기간 계산 (v0.6.0: 모든 통계는 앱을 설치한 날부터) */
object StatsPeriod {

    /** 그 날이 속한 주의 월요일 (홈 주간 달력과 같은 월~일 기준) */
    fun weekStart(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())

    /**
     * 통계 시작일 = 설치일. 단, 너무 오래된 기록까지 매번 계산하지 않도록 최근 maxDays 일로 제한하고,
     * 설치일이 오늘보다 뒤로 잡히는 경우(시계 변경 등)는 오늘로 맞춥니다.
     */
    fun startDate(installDate: LocalDate, today: LocalDate, maxDays: Long): LocalDate {
        val earliest = today.minusDays(maxDays - 1)
        return when {
            installDate.isAfter(today) -> today
            installDate.isBefore(earliest) -> earliest
            else -> installDate
        }
    }

    /** 시작일 기준 며칠째인지 (설치한 날 = 1일째) */
    fun dayNumber(start: LocalDate, today: LocalDate): Int =
        (java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceAtLeast(1)
}
