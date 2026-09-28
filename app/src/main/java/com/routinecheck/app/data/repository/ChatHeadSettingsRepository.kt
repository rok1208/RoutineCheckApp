package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.dao.ChatHeadSettingsDao
import com.routinecheck.app.data.local.entity.ChatHeadSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 루틴 완료 시 챗헤드 동작 */
enum class OnCompleteBehavior { KEEP, MINIMIZE, HIDE }

/** 바 상태일 때 붙는 화면 쪽 */
enum class EdgeSide { LEFT, RIGHT }

/** 화면/서비스 코드에서 쓰는 챗헤드 설정 (enum으로 타입 안전하게) */
data class ChatHeadSettings(
    val opacity: Float = 1.0f,
    val onCompleteBehavior: OnCompleteBehavior = OnCompleteBehavior.MINIMIZE,
    val iconUri: String? = null,
    val edgeSide: EdgeSide = EdgeSide.RIGHT
)

/**
 * 챗헤드 설정 저장/조회.
 * - 저장된 값이 없으면 ChatHeadSettings()의 기본값을 돌려줍니다 (처음 실행 때 행을 미리 넣을 필요 없음).
 * - ChatHeadService는 observeSettings()를 구독하면 설정 화면에서 값을 바꾸는 즉시 반영됩니다.
 * 담당: 변공록
 */
@Singleton
class ChatHeadSettingsRepository @Inject constructor(
    private val dao: ChatHeadSettingsDao
) {
    fun observeSettings(): Flow<ChatHeadSettings> =
        dao.observe().map { it?.toModel() ?: ChatHeadSettings() }

    suspend fun getSettings(): ChatHeadSettings =
        dao.getOnce()?.toModel() ?: ChatHeadSettings()

    suspend fun setOpacity(value: Float) =
        update { it.copy(opacity = value.coerceIn(MIN_OPACITY, MAX_OPACITY)) }

    suspend fun setOnCompleteBehavior(behavior: OnCompleteBehavior) =
        update { it.copy(onCompleteBehavior = behavior) }

    /** null을 넘기면 기본 앱 로고로 되돌립니다. */
    suspend fun setIconUri(uri: String?) =
        update { it.copy(iconUri = uri) }

    suspend fun setEdgeSide(side: EdgeSide) =
        update { it.copy(edgeSide = side) }

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
    edgeSide = runCatching { EdgeSide.valueOf(edgeSide) }
        .getOrDefault(EdgeSide.RIGHT)
)

private fun ChatHeadSettings.toEntity() = ChatHeadSettingsEntity(
    opacity = opacity,
    onCompleteBehavior = onCompleteBehavior.name,
    iconUri = iconUri,
    edgeSide = edgeSide.name
)
