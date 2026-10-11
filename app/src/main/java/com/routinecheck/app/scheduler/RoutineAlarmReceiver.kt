package com.routinecheck.app.scheduler

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.routinecheck.app.MainActivity
import com.routinecheck.app.R
import com.routinecheck.app.chathead.ChatHeadController
import com.routinecheck.app.chathead.ChatHeadService
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.data.repository.RoutineWithApps
import com.routinecheck.app.notification.NotificationChannels
import com.routinecheck.app.util.PermissionUtils
import com.routinecheck.app.util.TimeText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 루틴 알람 수신.
 *  1) 다음 회차 알람 다시 등록
 *  2) "○○ 루틴 시간이에요" 알림 표시 (챗헤드 열기 / N분 후 다시 버튼)
 *  3) 챗헤드 띄우기 — 자동 시작이 켜진 루틴이면 바로 펼쳐서 루틴 앱을 보여줌
 */
@AndroidEntryPoint
class RoutineAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var routineRepository: RoutineRepository
    @Inject lateinit var settingsRepository: ChatHeadSettingsRepository
    @Inject lateinit var scheduler: RoutineAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_ID, -1L)
        if (routineId < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    RoutineAlarmScheduler.ACTION_SNOOZE_REQUEST -> {
                        NotificationManagerCompat.from(context).cancel(notificationId(routineId))
                        val minutes = routineRepository.getRoutine(routineId)?.snoozeMinutes ?: 5
                        scheduler.scheduleSnooze(routineId, minutes)
                    }
                    RoutineAlarmScheduler.ACTION_FIRE, RoutineAlarmScheduler.ACTION_SNOOZE -> {
                        val rwa = routineRepository.getRoutineWithApps(routineId) ?: return@launch
                        if (!rwa.routine.isEnabled) return@launch
                        if (intent.action == RoutineAlarmScheduler.ACTION_FIRE) scheduler.schedule(rwa.routine)
                        showNotification(context, rwa)
                        val settings = settingsRepository.getSettings()
                        if (rwa.routine.autoStart) settingsRepository.setEnabled(true)
                        if (rwa.routine.autoStart || settings.enabled) {
                            withContext(Dispatchers.Main) {
                                ChatHeadController.show(context, expand = rwa.routine.autoStart)
                            }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, rwa: RoutineWithApps) {
        if (!PermissionUtils.hasNotificationPermission(context)) return
        NotificationChannels.ensure(context)
        val routine = rwa.routine
        val apps = rwa.apps.joinToString(", ") { it.appLabel }

        val openApp = PendingIntent.getActivity(
            context, notificationId(routine.routineId),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openChatHead = PendingIntent.getForegroundService(
            context, notificationId(routine.routineId),
            Intent(context, ChatHeadService::class.java).setAction(ChatHeadController.ACTION_EXPAND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, NotificationChannels.ROUTINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ ${routine.name} 시간이에요 (${TimeText.display(routine.startTime)})")
            .setContentText(if (apps.isBlank()) "루틴을 시작해 보세요" else "확인할 앱: $apps")
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (apps.isBlank()) "루틴을 시작해 보세요" else "확인할 앱: $apps"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, "챗헤드 열기", openChatHead)

        routine.snoozeMinutes?.let { minutes ->
            val snooze = PendingIntent.getBroadcast(
                context, notificationId(routine.routineId),
                Intent(context, RoutineAlarmReceiver::class.java)
                    .setAction(RoutineAlarmScheduler.ACTION_SNOOZE_REQUEST)
                    .putExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_ID, routine.routineId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "${minutes}분 후 다시", snooze)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId(routine.routineId), builder.build())
        } catch (_: SecurityException) {
            // 알림 권한이 방금 꺼진 경우
        }
    }

    private fun notificationId(routineId: Long): Int = 2000 + routineId.toInt()
}
