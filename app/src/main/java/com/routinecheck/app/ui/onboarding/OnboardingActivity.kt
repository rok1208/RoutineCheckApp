package com.routinecheck.app.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.routinecheck.app.MainActivity
import com.routinecheck.app.system.PermissionUtils
import com.routinecheck.app.ui.RoutineCheckTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱의 첫 진입 화면 (LAUNCHER).
 * 필수 권한이 이미 모두 있으면 바로 MainActivity로 넘어가고, 없으면 권한 안내를 보여줍니다.
 *
 *  - 오버레이 / 정확한 알람 / 알림 접근 / 배터리 최적화 → 설정 앱의 해당 화면으로 이동
 *  - 알림(POST_NOTIFICATIONS) → 시스템 권한 다이얼로그 (Android 13+)
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refresh() }

    /** 홈 화면의 "해결하기"로 들어온 경우: 끝나면 홈으로 돌아가기만 하면 됨 */
    private val fromApp: Boolean get() = intent.getBooleanExtra(EXTRA_FROM_APP, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!fromApp && savedInstanceState == null && PermissionUtils.requiredPermissionsGranted(this)) {
            goToMain()
            return
        }
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            RoutineCheckTheme {
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
        runCatching {
            when (type) {
                PermissionType.OVERLAY -> settingsLauncher.launch(PermissionUtils.overlayPermissionIntent(this))
                PermissionType.NOTIFICATION ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                PermissionType.EXACT_ALARM -> settingsLauncher.launch(PermissionUtils.exactAlarmIntent(this))
                PermissionType.NOTIFICATION_LISTENER -> settingsLauncher.launch(PermissionUtils.notificationListenerIntent(this))
                PermissionType.BATTERY_OPTIMIZATION -> settingsLauncher.launch(PermissionUtils.batteryOptimizationIntent(this))
            }
            Unit
        }.onFailure {
            // 일부 제조사 기기에서 특정 설정 화면이 없을 때: 앱 정보 화면으로 대신 이동
            settingsLauncher.launch(PermissionUtils.appDetailsIntent(this))
        }
    }

    private fun goToMain() {
        if (!fromApp) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_FROM_APP = "from_app"
    }
}
