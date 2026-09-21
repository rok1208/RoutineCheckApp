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
    val allGranted: Boolean get() = steps.isNotEmpty() && steps.all { it.isGranted }

    /** 아직 허용 안 된 항목 중 화면에 보이는 순서로 첫 번째. null이면 전부 허용됨. */
    val nextUngranted: PermissionStepUiState? get() = steps.firstOrNull { !it.isGranted }
}

/**
 * 온보딩(① 온보딩) 화면의 상태를 관리합니다.
 * Activity가 화면(설정 앱 등)에서 돌아올 때마다 refresh()를 호출해 상태를 다시 읽습니다.
 * 담당: 변공록
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = OnboardingUiState(
            steps = listOf(
                PermissionStepUiState(
                    type = PermissionType.OVERLAY,
                    title = "다른 앱 위에 표시",
                    description = "챗헤드를 화면 위에 띄우기 위해 필요해요",
                    isGranted = PermissionUtils.hasOverlayPermission(context)
                ),
                PermissionStepUiState(
                    type = PermissionType.NOTIFICATION,
                    title = "알림",
                    description = "루틴 시간 알림을 보내기 위해 필요해요",
                    isGranted = PermissionUtils.hasNotificationPermission(context)
                ),
                PermissionStepUiState(
                    type = PermissionType.BATTERY_OPTIMIZATION,
                    title = "배터리 사용량 최적화 제외",
                    description = "백그라운드에서 챗헤드/알림이 꺼지지 않도록 해줘요",
                    isGranted = PermissionUtils.isIgnoringBatteryOptimizations(context)
                )
            )
        )
    }
}
