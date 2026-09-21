package com.routinecheck.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * TODO(석광현): 기기 재부팅 후 저장된 모든 루틴의 알람을 다시 등록
 *   RoutineRepository.getRoutines()를 한 번 읽어서 RoutineAlarmScheduler.schedule() 반복 호출
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO: 저장된 루틴 전체 재등록
    }
}
