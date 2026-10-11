package com.routinecheck.app.onboarding

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
