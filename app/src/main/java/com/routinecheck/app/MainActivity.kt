package com.routinecheck.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.routinecheck.app.chathead.ChatHeadController
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.ui.navigation.AppRoot
import com.routinecheck.app.ui.theme.RoutineCheckTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 앱 화면 진입점. 하단 탭(홈 · 루틴 · 통계)과 모든 화면 이동은 ui/navigation/AppNavHost.kt 에서 처리합니다.
 * 챗헤드의 "앱 관리"/"설정" 버튼은 EXTRA_START_ROUTE 로 원하는 화면을 바로 엽니다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var chatHeadSettingsRepository: ChatHeadSettingsRepository

    /** 챗헤드에서 들어온 "이 화면 열어줘" 요청 (한 번 소비하면 null) */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_START_ROUTE)
        setContent {
            RoutineCheckTheme {
                AppRoot(
                    pendingRoute = pendingRoute.value,
                    onRouteConsumed = { pendingRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_START_ROUTE)?.let { pendingRoute.value = it }
    }

    override fun onResume() {
        super.onResume()
        // 챗헤드가 켜져 있어야 하는데 (메모리 정리 등으로) 꺼져 있었다면 다시 띄움
        lifecycleScope.launch {
            if (chatHeadSettingsRepository.getSettings().enabled) ChatHeadController.show(this@MainActivity)
        }
    }

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
    }
}
