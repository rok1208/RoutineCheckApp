package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 챗헤드 설정 테이블. 앱 전체에 공통으로 적용되는 설정이라 행이 항상 1개뿐입니다.
 *
 * enum처럼 쓰는 값(onCompleteBehavior, edgeSide, uiStyle)은 DB에는 문자열로 저장하고,
 * 코드에서는 ChatHeadSettingsRepository가 enum으로 바꿔서 제공합니다.
 *
 * v4 (2026-10-09 방향 전환): 챗헤드가 앱의 핵심 기능이 되면서
 *  - uiStyle  : 펼침 방식. "CAROUSEL"(휠 피커, 기본값) / "CONTAINER"(사각형 목록, 대체 스타일)
 *  - enabled  : 챗헤드를 켜 둘지 여부 (재부팅 후 자동으로 다시 띄울지 판단에도 사용)
 * 두 컬럼이 추가되었습니다.
 */
@Entity(tableName = "chathead_settings")
data class ChatHeadSettingsEntity(
    /** 항상 1. 행이 하나뿐이라는 의미로 고정값을 씁니다. */
    @PrimaryKey
    val id: Int = SINGLETON_ID,

    /** 챗헤드 투명도. 0.3(연함) ~ 1.0(불투명) */
    val opacity: Float = 1.0f,

    /** 루틴 완료 시 동작: "KEEP"(유지) / "MINIMIZE"(가장자리 바로 축소) / "HIDE"(숨김) */
    val onCompleteBehavior: String = "MINIMIZE",

    /** 사용자가 고른 챗헤드 이미지 파일 경로(file://...). null이면 기본 아이콘 */
    val iconUri: String? = null,

    /** 바 상태로 축소될 때 붙는 쪽: "LEFT" / "RIGHT" */
    val edgeSide: String = "RIGHT",

    /** 펼침 UI 스타일: "CAROUSEL"(기본) / "CONTAINER" */
    val uiStyle: String = "CAROUSEL",

    /** 챗헤드 사용 여부 */
    val enabled: Boolean = true
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
