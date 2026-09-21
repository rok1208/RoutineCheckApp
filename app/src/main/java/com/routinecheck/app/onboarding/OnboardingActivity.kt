package com.routinecheck.app.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.routinecheck.app.MainActivity
import com.routinecheck.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱의 첫 진입 화면 (LAUNCHER). 필수 권한을 모두 받으면 MainActivity로 이동합니다.
 *
 * 권한별로 요청 방식이 다른 이유:
 *  - 오버레이 / 배터리 최적화 → 설정 앱의 특정 화면으로 이동해야 함 (startActivity)
 *  - 알림(POST_NOTIFICATIONS) → 일반 런타임 권한이라 시스템 다이얼로그로 요청 가능
 *
 * 설정 화면에서 돌아오면 onResume에서 다시 상태를 읽어야 하므로 refresh()를 호출합니다.
 * 담당: 변공록
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    // 알림 권한(POST_NOTIFICATIONS)은 런타임 권한 다이얼로그로 요청
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refresh()
        }

    // 오버레이 / 배터리 최적화는 설정 화면으로 이동 후 돌아왔을 때 refresh
    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()

            MaterialTheme {
                OnboardingScreen(
                    uiState = uiState,
                    onRequestPermission = ::requestPermission,
                    onFinish = ::goToMain
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun requestPermission(type: PermissionType) {
        when (type) {
            PermissionType.OVERLAY ->
                settingsLauncher.launch(PermissionUtils.overlayPermissionIntent(this))

            PermissionType.NOTIFICATION ->
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)

            PermissionType.BATTERY_OPTIMIZATION ->
                settingsLauncher.launch(PermissionUtils.batteryOptimizationIntent(this))
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
