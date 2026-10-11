package com.routinecheck.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.routinecheck.app.chathead.ChatHeadController
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.data.repository.RoutineRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 재부팅 · 앱 업데이트 · 시간/시간대 변경 · 정확 알람 권한 변경 시
 * 모든 루틴 알람을 다시 계산해서 등록하고, 챗헤드가 켜져 있었다면 다시 띄웁니다.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var routineRepository: RoutineRepository
    @Inject lateinit var settingsRepository: ChatHeadSettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                routineRepository.rescheduleAll()
                val restartChatHead = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
                if (restartChatHead && settingsRepository.getSettings().enabled) {
                    withContext(Dispatchers.Main) { ChatHeadController.show(context) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
