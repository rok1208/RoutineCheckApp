package com.routinecheck.app.util

import android.content.Intent
import android.provider.AlarmClock
import com.routinecheck.app.data.local.entity.RoutineEntity
import java.time.DayOfWeek
import java.util.Calendar

/**
 * "기존 알람 앱과 연동" 옵션: 시계 앱에 같은 시각·요일의 알람을 만들어 줍니다.
 * (안드로이드 표준 AlarmClock 인텐트. 시계 앱이 만든 알람은 시계 앱에서 지워야 합니다)
 */
object SystemAlarmLinker {

    fun setAlarmIntent(routine: RoutineEntity): Intent {
        val time = TimeText.parse(routine.startTime)
        val days = ArrayList(DayMask.days(routine.daysOfWeek).map { it.toCalendarDay() })
        return Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "루틴: ${routine.name}")
            .putExtra(AlarmClock.EXTRA_DAYS, days)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    }

    /** java.time 요일 → Calendar 요일 (일=1 … 토=7) */
    private fun DayOfWeek.toCalendarDay(): Int = when (this) {
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
    }
}
