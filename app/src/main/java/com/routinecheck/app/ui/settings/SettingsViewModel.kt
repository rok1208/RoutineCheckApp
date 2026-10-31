package com.routinecheck.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.routinecheck.app.chathead.ChatHeadController
import com.routinecheck.app.chathead.ChatHeadIconStore
import com.routinecheck.app.data.repository.AlertMode
import com.routinecheck.app.data.repository.ChatHeadSettings
import com.routinecheck.app.data.repository.ChatHeadSettingsRepository
import com.routinecheck.app.data.repository.ChatHeadStyle
import com.routinecheck.app.data.repository.EdgeSide
import com.routinecheck.app.data.repository.OnCompleteBehavior
import com.routinecheck.app.system.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionItem(
    val title: String,
    val description: String,
    val required: Boolean,
    val granted: Boolean,
    val intent: Intent
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChatHeadSettingsRepository
) : ViewModel() {

    val settings: StateFlow<ChatHeadSettings?> = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _permissions = MutableStateFlow(readPermissions())
    val permissions: StateFlow<List<PermissionItem>> = _permissions.asStateFlow()

    fun refreshPermissions() { _permissions.value = readPermissions() }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(enabled)
        if (enabled) ChatHeadController.show(context) else ChatHeadController.hide(context)
    }

    fun setStyle(style: ChatHeadStyle) = viewModelScope.launch { repository.setUiStyle(style) }
    fun setOpacity(value: Float) = viewModelScope.launch { repository.setOpacity(value) }
    fun setOnComplete(behavior: OnCompleteBehavior) = viewModelScope.launch { repository.setOnCompleteBehavior(behavior) }
    fun setEdgeSide(side: EdgeSide) = viewModelScope.launch { repository.setEdgeSide(side) }
    fun setAlertMode(mode: AlertMode) = viewModelScope.launch { repository.setAlertMode(mode) }

    fun setIcon(uri: Uri) = viewModelScope.launch {
        val saved = withContext(Dispatchers.IO) { ChatHeadIconStore.save(context, uri) }
        if (saved != null) repository.setIconUri(saved)
    }

    fun resetIcon() = viewModelScope.launch {
        withContext(Dispatchers.IO) { ChatHeadIconStore.delete(context) }
        repository.setIconUri(null)
    }

    private fun readPermissions(): List<PermissionItem> = listOf(
        PermissionItem(
            "다른 앱 위에 표시", "챗헤드를 화면 위에 띄워요", true,
            PermissionUtils.hasOverlayPermission(context), PermissionUtils.overlayPermissionIntent(context)
        ),
        PermissionItem(
            "알림", "루틴 시작 알림을 보내요", false,
            PermissionUtils.hasNotificationPermission(context),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        ),
        PermissionItem(
            "정확한 알람", "루틴 시각에 늦지 않게 알려요", false,
            PermissionUtils.canScheduleExactAlarms(context), PermissionUtils.exactAlarmIntent(context)
        ),
        PermissionItem(
            "알림 접근", "메신저 등의 미확인 알림 수를 챗헤드 배지로 보여줘요", false,
            PermissionUtils.hasNotificationListenerAccess(context), PermissionUtils.notificationListenerIntent(context)
        ),
        PermissionItem(
            "배터리 최적화 제외", "목록에서 '루틴 체크'를 '최적화 안 함'으로 바꾸면 챗헤드가 꺼지지 않아요", false,
            PermissionUtils.isIgnoringBatteryOptimizations(context), PermissionUtils.batteryOptimizationIntent(context)
        )
    ).map { it.copy(intent = it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
