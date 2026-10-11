package com.routinecheck.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    /** 챗헤드 유지용 포그라운드 서비스 알림 (조용히) */
    const val CHATHEAD = "chathead"
    /** 루틴 시간 알림 (소리/헤드업) */
    const val ROUTINE = "routine"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHATHEAD, "챗헤드 실행 중", NotificationManager.IMPORTANCE_MIN).apply {
                description = "챗헤드를 화면에 띄워두기 위해 필요한 알림입니다"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ROUTINE, "루틴 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "루틴 시작 시각에 알려드립니다"
            }
        )
    }
}
