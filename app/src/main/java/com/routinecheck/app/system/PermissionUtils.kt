package com.routinecheck.app.system

import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 권한 상태 확인 + 요청 Intent 생성.
 *
 * 필수: 다른 앱 위에 표시(챗헤드), 알림, 정확한 알람(루틴 정각 알림)
 * 권장: 배터리 최적화 제외, 알림 접근(챗헤드 배지)
 */
object PermissionUtils {

    /** 다른 앱 위에 표시 (챗헤드 오버레이) */
    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlayPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** 알림 권한 (Android 13 미만은 항상 허용된 것으로 취급) */
    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

    /** 정확한 알람 (Android 12+) */
    fun canScheduleExactAlarms(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else true

    fun exactAlarmIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            appDetailsIntent(context)
        }

    /** 배터리 최적화 제외 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** 알림 접근 (NotificationListenerService — 챗헤드 미확인 알림 배지) */
    fun hasNotificationListenerAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun notificationListenerIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, AppNotificationListenerService::class.java).flattenToString()
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** 앱을 쓰기 위해 반드시 필요한 권한이 모두 있는지 (v5부터 오버레이만 필수, 알림 관련은 선택) */
    fun requiredPermissionsGranted(context: Context): Boolean =
        hasOverlayPermission(context)
}
