package com.routinecheck.app

import android.app.Application
import com.routinecheck.app.data.repository.RoutineRepository
import com.routinecheck.app.system.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 진입점. Hilt DI 그래프가 여기서부터 생성됩니다.
 * 앱 프로세스가 시작될 때마다 알림 채널을 준비하고 루틴 알람을 다시 계산해 둡니다
 * (알람이 어긋나거나 빠지는 일을 막기 위한 안전장치).
 */
@HiltAndroidApp
class RoutineCheckApp : Application() {

    @Inject lateinit var routineRepository: RoutineRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        appScope.launch { runCatching { routineRepository.rescheduleAll() } }
    }
}
