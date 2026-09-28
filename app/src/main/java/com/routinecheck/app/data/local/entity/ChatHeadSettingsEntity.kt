package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 챗헤드 설정 테이블. 앱 전체에 공통으로 적용되는 설정이라 행이 항상 1개뿐입니다.
 * (루틴이나 앱마다 따로 두는 값이 아님)
 *
 * enum처럼 쓰는 값(onCompleteBehavior, edgeSide)은 DB에는 문자열로 저장하고,
 * 코드에서는 ChatHeadSettingsRepository가 enum으로 바꿔서 제공합니다.
 * 담당: 변공록 (챗헤드)
 */
@Entity(tableName = "chathead_settings")
data class ChatHeadSettingsEntity(
    /** 항상 1. 행이 하나뿐이라는 의미로 고정값을 씁니다. */
    @PrimaryKey
    val id: Int = SINGLETON_ID,

    /** 챗헤드 투명도. 0.3(연함) ~ 1.0(불투명) */
    val opacity: Float = 1.0f,

    /** 루틴 완료 시 동작: "KEEP"(유지) / "MINIMIZE"(가장자리 바로 축소) / "HIDE"(비활성화) */
    val onCompleteBehavior: String = "MINIMIZE",

    /** 사용자가 고른 챗헤드 이미지의 Uri 문자열. null이면 기본 앱 로고 */
    val iconUri: String? = null,

    /** 바 상태로 축소될 때 붙는 쪽: "LEFT" / "RIGHT" */
    val edgeSide: String = "RIGHT"
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
