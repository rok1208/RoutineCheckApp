package com.routinecheck.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.system.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────── PermissionStep

/** 온보딩 화면에 표시할 권한 항목 */
enum class PermissionType {
    OVERLAY,
    NOTIFICATION,
    EXACT_ALARM,
    NOTIFICATION_LISTENER,
    BATTERY_OPTIMIZATION
}

data class PermissionStepUiState(
    val type: PermissionType,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    /** false면 "권장" — 허용하지 않아도 시작할 수 있음 */
    val isRequired: Boolean = true
)

// ─────────────────────────────── OnboardingViewModel

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
 *
 * v0.6.0: 첫 실행에서는 챗헤드에 꼭 필요한 "다른 앱 위에 표시" 하나만 요청합니다.
 * 알림 · 정확한 알람 · 알림 접근 · 배터리 최적화 제외는 그 기능을 쓰려는 순간에 요청해요
 * (ui/common/PermissionRequest.kt 참고). "나중에 할게요"로 권한 없이 들어갈 수도 있고,
 * 그때는 챗헤드를 꺼 둔 채 시작하고 홈에서 챗헤드를 켤 때 다시 요청합니다.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatHeadSettingsRepository: ChatHeadSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = OnboardingUiState(
            steps = listOf(
                PermissionStepUiState(
                    PermissionType.OVERLAY, "다른 앱 위에 표시",
                    "어느 화면에서든 챗헤드를 띄워 앱을 바로 열 수 있게 해줘요",
                    PermissionUtils.hasOverlayPermission(context)
                )
            )
        )
    }

    /** 권한 없이 시작: 챗헤드를 꺼 둔 상태로 들어감 */
    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            chatHeadSettingsRepository.setEnabled(false)
            onDone()
        }
    }
}
