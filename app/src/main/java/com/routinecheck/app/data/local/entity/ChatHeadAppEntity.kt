package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 챗헤드 전용 앱 목록 (v4 신규).
 *
 * 챗헤드가 앱의 중심이 되면서, 루틴과 상관없이 "항상 챗헤드에서 바로 열고 싶은 앱"을
 * 따로 등록할 수 있게 했습니다. 캐러셀에는
 *   ① 지금 진행 중인 루틴의 앱(미완료 우선) → ② 이 테이블의 앱(sortOrder 순)
 * 순서로 표시되고, 같은 앱이 양쪽에 있으면 한 번만 보입니다.
 */
@Entity(tableName = "chathead_apps")
data class ChatHeadAppEntity(
    @PrimaryKey
    val packageName: String,

    /** 등록 시점의 표시 이름 캐시 */
    val appLabel: String,

    /** 캐러셀/컨테이너에 보여줄 순서 (작을수록 앞) */
    val sortOrder: Int = 0,

    val addedAt: Long = System.currentTimeMillis()
)
