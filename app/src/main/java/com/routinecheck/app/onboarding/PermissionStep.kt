package com.routinecheck.app.onboarding

/** 온보딩 화면에 표시할 권한 항목 하나 */
enum class PermissionType {
    OVERLAY,
    NOTIFICATION,
    BATTERY_OPTIMIZATION
}

data class PermissionStepUiState(
    val type: PermissionType,
    val title: String,
    val description: String,
    val isGranted: Boolean
)
