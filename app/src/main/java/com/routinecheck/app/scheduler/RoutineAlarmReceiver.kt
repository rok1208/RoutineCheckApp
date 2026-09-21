package com.routinecheck.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * TODO(석광현): 알람 수신 시 챗헤드 자동 노출 또는 알림 발송
 *   AndroidManifest.xml 에 이미 receiver로 등록해둠.
 */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO: ChatHeadService 시작 또는 알림(Notification) 표시
    }
}
