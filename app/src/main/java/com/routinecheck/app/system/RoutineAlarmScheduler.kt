package com.routinecheck.app.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.routinecheck.app.data.local.RoutineEntity
import com.routinecheck.app.domain.RoutineSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 루틴 정각 알람 등록/취소 (결정사항: AlarmManager 정확 알람).
 *
 * 반복 알람(setRepeating)은 정확하지 않아서, "다음 한 번"만 정확 알람으로 걸고
 * 알람이 울리면 RoutineAlarmReceiver가 그다음 회차를 다시 거는 방식입니다.
 * 정확 알람 권한이 꺼져 있으면 약간 늦을 수 있는 일반 알람으로 대신 등록합니다.
 */
@Singleton
class RoutineAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** 다음 회차 알람 등록 (기존 것은 교체). 비활성 루틴이면 취소만 */
    fun schedule(routine: RoutineEntity, now: LocalDateTime = LocalDateTime.now()) {
        cancelMain(routine.routineId)
        if (!routine.isEnabled) return
        val next = RoutineSchedule.nextTrigger(routine.startTime, routine.daysOfWeek, now, routine.date) ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(millis, pendingIntent(routine.routineId, ACTION_FIRE, create = true)!!)
        Log.d(TAG, "루틴 ${routine.routineId} 다음 알람: $next")
    }

    /** 알림의 "N분 후 다시" 버튼 */
    fun scheduleSnooze(routineId: Long, minutes: Int) {
        val millis = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(millis, pendingIntent(routineId, ACTION_SNOOZE, create = true)!!)
    }

    fun cancel(routineId: Long) {
        cancelMain(routineId)
        pendingIntent(routineId, ACTION_SNOOZE, create = false)?.let { alarmManager.cancel(it); it.cancel() }
    }

    private fun cancelMain(routineId: Long) {
        pendingIntent(routineId, ACTION_FIRE, create = false)?.let { alarmManager.cancel(it); it.cancel() }
    }

    private fun setAlarm(triggerAtMillis: Long, pi: PendingIntent) {
        try {
            if (PermissionUtils.canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            // 등록 직전에 권한이 회수된 경우
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(routineId: Long, action: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, RoutineAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_ROUTINE_ID, routineId)
        val requestCode = (routineId * 2 + if (action == ACTION_SNOOZE) 1 else 0).toInt()
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    companion object {
        private const val TAG = "RoutineAlarm"
        const val ACTION_FIRE = "com.routinecheck.app.alarm.FIRE"
        const val ACTION_SNOOZE = "com.routinecheck.app.alarm.SNOOZE"
        const val ACTION_SNOOZE_REQUEST = "com.routinecheck.app.alarm.SNOOZE_REQUEST"
        const val EXTRA_ROUTINE_ID = "routineId"
    }
}
