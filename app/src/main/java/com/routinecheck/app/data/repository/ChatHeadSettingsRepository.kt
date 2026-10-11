package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.dao.ChatHeadSettingsDao
import com.routinecheck.app.data.local.entity.ChatHeadSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 루틴 완료 시 챗헤드 동작 */
enum class OnCompleteBehavior(val label: String) {
    KEEP("그대로 유지"),
    MINIMIZE("가장자리 바로 축소"),
    HIDE("숨기기 (다음 루틴 알람이나 앱을 열 때 다시 표시)")
}

/** 바 상태일 때 붙는 화면 쪽 */
enum class EdgeSide(val label: String) { LEFT("왼쪽"), RIGHT("오른쪽") }

/** 챗헤드 펼침 방식. CAROUSEL이 기본값, CONTAINER는 설정에서 고르는 대체 스타일 */
enum class ChatHeadStyle(val label: String, val description: String) {
    CAROUSEL("휠 캐러셀 (기본)", "위아래로 돌려서 고르기. 가운데 카드가 선택된 앱"),
    CONTAINER("컨테이너 (목록형)", "사각형 패널에 앱을 한 번에 나열")
}

/** 루틴 시작 알림 방식 (휴대폰이 무음·진동 모드면 휴대폰 설정이 우선) */
enum class AlertMode(val label: String) {
    SOUND_VIBRATE("소리 + 진동"),
    SOUND("소리"),
    VIBRATE("진동")
}

/** 화면/서비스 코드에서 쓰는 챗헤드 설정 (enum으로 타입 안전하게) */
data class ChatHeadSettings(
    val opacity: Float = 1.0f,
    val onCompleteBehavior: OnCompleteBehavior = OnCompleteBehavior.MINIMIZE,
    val iconUri: String? = null,
    val edgeSide: EdgeSide = EdgeSide.RIGHT,
    val uiStyle: ChatHeadStyle = ChatHeadStyle.CAROUSEL,
    val enabled: Boolean = true,
    val alertMode: AlertMode = AlertMode.SOUND_VIBRATE
)

/**
 * 챗헤드 설정 저장/조회.
 * 저장된 값이 없으면 ChatHeadSettings() 기본값을 돌려줍니다.
 * ChatHeadService가 observeSettings()를 구독하므로 설정 화면에서 바꾸는 즉시 반영됩니다.
 */
@Singleton
class ChatHeadSettingsRepository @Inject constructor(
    private val dao: ChatHeadSettingsDao
) {
    fun observeSettings(): Flow<ChatHeadSettings> =
        dao.observe().map { it?.toModel() ?: ChatHeadSettings() }.distinctUntilChanged()

    suspend fun getSettings(): ChatHeadSettings =
        dao.getOnce()?.toModel() ?: ChatHeadSettings()

    suspend fun setOpacity(value: Float) =
        update { it.copy(opacity = value.coerceIn(MIN_OPACITY, MAX_OPACITY)) }

    suspend fun setOnCompleteBehavior(behavior: OnCompleteBehavior) =
        update { it.copy(onCompleteBehavior = behavior) }

    /** null을 넘기면 기본 아이콘으로 되돌립니다. */
    suspend fun setIconUri(uri: String?) = update { it.copy(iconUri = uri) }

    suspend fun setEdgeSide(side: EdgeSide) = update { it.copy(edgeSide = side) }

    suspend fun setUiStyle(style: ChatHeadStyle) = update { it.copy(uiStyle = style) }

    suspend fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    suspend fun setAlertMode(mode: AlertMode) = update { it.copy(alertMode = mode) }

    private suspend fun update(transform: (ChatHeadSettings) -> ChatHeadSettings) {
        dao.save(transform(getSettings()).toEntity())
    }

    companion object {
        const val MIN_OPACITY = 0.3f
        const val MAX_OPACITY = 1.0f
    }
}

private fun ChatHeadSettingsEntity.toModel() = ChatHeadSettings(
    opacity = opacity,
    onCompleteBehavior = runCatching { OnCompleteBehavior.valueOf(onCompleteBehavior) }
        .getOrDefault(OnCompleteBehavior.MINIMIZE),
    iconUri = iconUri,
    edgeSide = runCatching { EdgeSide.valueOf(edgeSide) }.getOrDefault(EdgeSide.RIGHT),
    uiStyle = runCatching { ChatHeadStyle.valueOf(uiStyle) }.getOrDefault(ChatHeadStyle.CAROUSEL),
    enabled = enabled,
    alertMode = runCatching { AlertMode.valueOf(alertMode) }.getOrDefault(AlertMode.SOUND_VIBRATE)
)

private fun ChatHeadSettings.toEntity() = ChatHeadSettingsEntity(
    opacity = opacity,
    onCompleteBehavior = onCompleteBehavior.name,
    iconUri = iconUri,
    edgeSide = edgeSide.name,
    uiStyle = uiStyle.name,
    enabled = enabled,
    alertMode = alertMode.name
)
