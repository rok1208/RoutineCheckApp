package com.routinecheck.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.routinecheck.app.data.repository.AlertMode

/**
 * 알림 채널.
 * 안드로이드 8.0+에서는 소리·진동이 "채널"에 묶여 있고, 한 번 만든 채널의 소리·진동은 앱이 바꿀 수 없습니다.
 * 그래서 루틴 알림 방식마다 채널을 하나씩 만들어 두고, 설정값에 맞는 채널로 알림을 보냅니다.
 */
object NotificationChannels {
    /** 챗헤드 유지용 포그라운드 서비스 알림 (조용히) */
    const val CHATHEAD = "chathead"

    /** 루틴 시간 알림 — 방식별 채널 */
    const val ROUTINE_SOUND_VIBRATE = "routine_sound_vibrate"
    const val ROUTINE_SOUND = "routine_sound"
    const val ROUTINE_VIBRATE = "routine_vibrate"

    /** v0.2.0까지 쓰던 루틴 채널 (지금은 지움) */
    private const val LEGACY_ROUTINE = "routine"

    private val VIBRATION = longArrayOf(0, 400, 200, 400)

    fun routineChannel(mode: AlertMode): String = when (mode) {
        AlertMode.SOUND_VIBRATE -> ROUTINE_SOUND_VIBRATE
        AlertMode.SOUND -> ROUTINE_SOUND
        AlertMode.VIBRATE -> ROUTINE_VIBRATE
    }

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(LEGACY_ROUTINE)
        nm.createNotificationChannel(
            NotificationChannel(CHATHEAD, "챗헤드 실행 중", NotificationManager.IMPORTANCE_MIN).apply {
                description = "챗헤드를 화면에 띄워두기 위해 필요한 알림입니다"
                setShowBadge(false)
            }
        )
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(ROUTINE_SOUND_VIBRATE, "루틴 알림 (소리 + 진동)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "루틴 시작 시각에 소리와 진동으로 알려드립니다"
                setSound(sound, audio)
                enableVibration(true)
                vibrationPattern = VIBRATION
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ROUTINE_SOUND, "루틴 알림 (소리)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "루틴 시작 시각에 소리로만 알려드립니다"
                setSound(sound, audio)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ROUTINE_VIBRATE, "루틴 알림 (진동)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "루틴 시작 시각에 진동으로만 알려드립니다"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = VIBRATION
            }
        )
    }
}
