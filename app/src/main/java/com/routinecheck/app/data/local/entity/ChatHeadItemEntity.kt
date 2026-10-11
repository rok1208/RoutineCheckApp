package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 챗헤드에 들어가는 항목 하나 (v5 신규, 기존 chathead_apps를 대체).
 *
 * 앱 · 웹사이트 · 폴더를 한 테이블에 담아서, 같은 단계 안에서 순서를 자유롭게 정하고
 * 폴더 안에 앱/웹사이트/하위 폴더를 넣을 수 있게 했습니다.
 *  - type = "APP"    : packageName 사용
 *  - type = "LINK"   : url 사용 (예: https://www.naver.com)
 *  - type = "FOLDER" : 다른 항목의 parentId가 이 항목의 itemId를 가리킴
 *  - parentId = null 이면 최상단(챗헤드 첫 화면)
 */
@Entity(
    tableName = "chathead_items",
    indices = [Index(value = ["parentId"])]
)
data class ChatHeadItemEntity(
    @PrimaryKey(autoGenerate = true)
    val itemId: Long = 0,

    /** "APP" / "LINK" / "FOLDER" */
    val type: String,

    /** 앱 이름 / 웹사이트 이름 / 폴더 이름 */
    val title: String,

    val packageName: String? = null,

    val url: String? = null,

    /** 들어 있는 폴더의 itemId. null이면 최상단 */
    val parentId: Long? = null,

    /** 같은 폴더 안에서의 순서 (작을수록 앞) */
    val sortOrder: Int = 0,

    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_LINK = "LINK"
        const val TYPE_FOLDER = "FOLDER"
    }
}
