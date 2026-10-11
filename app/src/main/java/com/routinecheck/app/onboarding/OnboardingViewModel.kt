package com.routinecheck.app.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import com.routinecheck.app.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class OnboardingUiState(
    val steps: List<PermissionStepUiState> = emptyList()
) {
    /** 필수 권한이 모두 허용되면 시작 가능 */
    val requiredGranted: Boolean get() = steps.isNotEmpty() && steps.filter { it.isRequired }.all { it.isGranted }
    val allGranted: Boolean get() = steps.isNotEmpty() && steps.all { it.isGranted }

    /** 아직 허용 안 된 필수 항목 중 첫 번째 */
    val nextRequired: PermissionStepUiState? get() = steps.firstOrNull { it.isRequired && !it.isGranted }
}

/**
 * 온보딩 화면 상태. 설정 화면 등에서 돌아올 때마다 refresh()로 다시 읽습니다.
 * 필수: 오버레이 · 알림 · 정확한 알람 / 권장: 알림 접근(배지) · 배터리 최적화 제외
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = OnboardingUiState(
            steps = listOf(
                PermissionStepUiState(
                    PermissionType.OVERLAY, "다른 앱 위에 표시",
                    "챗헤드를 어느 화면에서든 띄우기 위해 필요해요",
                    PermissionUtils.hasOverlayPermission(context)
                ),
                PermissionStepUiState(
                    PermissionType.NOTIFICATION, "알림",
                    "루틴 시간이 되면 알려드려요",
                    PermissionUtils.hasNotificationPermission(context)
                ),
                PermissionStepUiState(
                    PermissionType.EXACT_ALARM, "정확한 알람",
                    "루틴 시각에 늦지 않게 정각에 알려드려요",
                    PermissionUtils.canScheduleExactAlarms(context)
                ),
                PermissionStepUiState(
                    PermissionType.NOTIFICATION_LISTENER, "알림 접근",
                    "메신저 등의 미확인 알림 수를 챗헤드에 배지로 보여줘요",
                    PermissionUtils.hasNotificationListenerAccess(context),
                    isRequired = false
                ),
                PermissionStepUiState(
                    PermissionType.BATTERY_OPTIMIZATION, "배터리 사용량 최적화 제외",
                    "백그라운드에서 챗헤드와 알람이 꺼지지 않게 해줘요",
                    PermissionUtils.isIgnoringBatteryOptimizations(context),
                    isRequired = false
                )
            )
        )
    }
}
