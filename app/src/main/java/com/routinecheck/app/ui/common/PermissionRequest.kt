package com.routinecheck.app.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.routinecheck.app.system.PermissionUtils

/**
 * 권한은 "그 기능을 쓰려는 순간"에 요청합니다 (v0.6.0).
 *
 * | 권한 | 요청 시점 |
 * |---|---|
 * | 다른 앱 위에 표시 | 첫 실행 (유일한 필수 권한) · 챗헤드를 켤 때 |
 * | 배터리 최적화 제외 | 챗헤드를 처음 켠 직후 |
 * | 알림 | 시작 알림을 켠 루틴/일정을 저장할 때 |
 * | 정확한 알람 | 알림이나 챗헤드 자동 펼치기를 켠 루틴/일정을 저장할 때 |
 * | 알림 접근 | 챗헤드 항목 화면에서 "알림 배지 켜기"를 누를 때 |
 *
 * 먼저 왜 필요한지 설명하는 창을 보여주고, [허용하러 가기]를 누를 때만 시스템 창/설정 화면으로 보냅니다.
 * [나중에]를 누르면 3일 동안은 자동으로 다시 묻지 않습니다 (사용자가 직접 누른 요청은 언제든 진행).
 */
enum class AppPermission(val title: String, val defaultReason: String) {
    OVERLAY(
        "다른 앱 위에 표시",
        "챗헤드를 어느 화면에서든 띄우려면 이 권한이 필요해요. 설정에서 '루틴 체크'를 찾아 허용해 주세요."
    ),
    NOTIFICATION(
        "알림",
        "루틴 시각에 알림을 보내려면 알림 권한이 필요해요."
    ),
    EXACT_ALARM(
        "정확한 알람",
        "허용하면 루틴 시각에 늦지 않고 정각에 알려드려요. 허용하지 않으면 몇 분 늦게 울릴 수 있어요."
    ),
    NOTIFICATION_LISTENER(
        "알림 접근",
        "메신저 등 앱의 미확인 알림 수를 챗헤드에 배지로 보여주려면 알림 접근이 필요해요. 알림 내용은 저장하거나 밖으로 보내지 않아요."
    ),
    BATTERY(
        "배터리 사용량 최적화 제외",
        "휴대폰이 절전을 위해 챗헤드와 루틴 알람을 꺼 버리지 않도록, 다음 화면의 목록에서 '루틴 체크'를 찾아 '최적화 안 함'으로 바꿔 주세요."
    );

    fun isGranted(context: Context): Boolean = when (this) {
        OVERLAY -> PermissionUtils.hasOverlayPermission(context)
        NOTIFICATION -> PermissionUtils.hasNotificationPermission(context)
        EXACT_ALARM -> PermissionUtils.canScheduleExactAlarms(context)
        NOTIFICATION_LISTENER -> PermissionUtils.hasNotificationListenerAccess(context)
        BATTERY -> PermissionUtils.isIgnoringBatteryOptimizations(context)
    }

    /** 설정 화면으로 보내는 권한의 Intent (알림 권한은 Android 13+ 에서 시스템 창을 씀) */
    fun settingsIntent(context: Context): Intent = when (this) {
        OVERLAY -> PermissionUtils.overlayPermissionIntent(context)
        NOTIFICATION -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        EXACT_ALARM -> PermissionUtils.exactAlarmIntent(context)
        NOTIFICATION_LISTENER -> PermissionUtils.notificationListenerIntent(context)
        BATTERY -> PermissionUtils.batteryOptimizationIntent(context)
    }
}

/** 한 번의 요청: 순서대로 묻고, 끝나면 허용된 권한 목록으로 onDone */
internal class PermissionRequest(
    val queue: List<AppPermission>,
    val requested: List<AppPermission>,
    val reasons: Map<AppPermission, String>,
    val onDone: (Set<AppPermission>) -> Unit
)

@Stable
class PermissionRequester internal constructor(private val context: Context) {
    internal var active by mutableStateOf<PermissionRequest?>(null)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * @param userInitiated 사용자가 직접 누른 요청이면 true ("나중에" 기록을 무시하고 항상 물어봄)
     * @param reasons 화면 상황에 맞춘 설명 (없으면 기본 설명)
     */
    fun request(
        permissions: List<AppPermission>,
        userInitiated: Boolean = false,
        reasons: Map<AppPermission, String> = emptyMap(),
        onDone: (Set<AppPermission>) -> Unit = {}
    ) {
        val missing = permissions.distinct().filter { !it.isGranted(context) && (userInitiated || !recentlyDeclined(it)) }
        val req = PermissionRequest(missing, permissions, reasons, onDone)
        if (missing.isEmpty()) finish(req) else active = req
    }

    internal fun proceed() {
        val req = active ?: return
        val rest = req.queue.drop(1)
        if (rest.isEmpty()) {
            active = null
            finish(req)
        } else {
            active = PermissionRequest(rest, req.requested, req.reasons, req.onDone)
        }
    }

    internal fun decline() {
        active?.queue?.firstOrNull()?.let {
            prefs.edit().putLong(KEY_DECLINED + it.name, System.currentTimeMillis()).apply()
        }
        proceed()
    }

    internal fun markNotificationDenied() {
        prefs.edit().putBoolean(KEY_NOTIF_DENIED, true).apply()
    }

    /** 알림 권한 시스템 창을 이미 거절한 적이 있으면 (다시 띄워도 바로 닫힘) 설정 화면으로 보냄 */
    internal fun notificationDeniedBefore(): Boolean = prefs.getBoolean(KEY_NOTIF_DENIED, false)

    private fun finish(req: PermissionRequest) {
        req.onDone(req.requested.filter { it.isGranted(context) }.toSet())
    }

    private fun recentlyDeclined(p: AppPermission): Boolean {
        val at = prefs.getLong(KEY_DECLINED + p.name, 0L)
        return at > 0 && System.currentTimeMillis() - at < DECLINE_QUIET_MILLIS
    }

    companion object {
        private const val PREFS_NAME = "permission_prompts"
        private const val KEY_DECLINED = "declined_"
        private const val KEY_NOTIF_DENIED = "notification_denied"
        private const val DECLINE_QUIET_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}

/** 화면마다 하나씩 만들어 쓰는 권한 요청기. 설명 창도 여기서 그려줌 */
@Composable
fun rememberPermissionRequester(): PermissionRequester {
    val context = LocalContext.current
    val requester = remember { PermissionRequester(context.applicationContext) }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requester.proceed()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) requester.markNotificationDenied()
        requester.proceed()
    }

    val req = requester.active
    val current = req?.queue?.firstOrNull()
    if (req != null && current != null) {
        AlertDialog(
            onDismissRequest = requester::decline,
            title = { Text("${current.title} 권한이 필요해요") },
            text = { Text(req.reasons[current] ?: current.defaultReason) },
            confirmButton = {
                TextButton(onClick = {
                    val useSystemDialog = current == AppPermission.NOTIFICATION &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !requester.notificationDeniedBefore()
                    if (useSystemDialog) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        runCatching { settingsLauncher.launch(current.settingsIntent(context)) }
                            .onFailure {
                                // 일부 제조사 기기에 해당 설정 화면이 없을 때: 앱 정보 화면으로
                                runCatching { settingsLauncher.launch(PermissionUtils.appDetailsIntent(context)) }
                                    .onFailure { requester.proceed() }
                            }
                    }
                }) { Text("허용하러 가기") }
            },
            dismissButton = { TextButton(onClick = requester::decline) { Text("나중에") } }
        )
    }
    return requester
}

/**
 * 챗헤드 켜기: 오버레이 권한이 있어야 켜고, 처음 켠 직후에 배터리 최적화 제외를 한 번 물어봄.
 * @param onEnable 권한이 확보됐을 때 실제로 켜는 동작
 */
fun PermissionRequester.enableChatHead(onEnable: () -> Unit) {
    request(listOf(AppPermission.OVERLAY), userInitiated = true) { granted ->
        if (AppPermission.OVERLAY in granted) {
            onEnable()
            request(listOf(AppPermission.BATTERY))
        }
    }
}
