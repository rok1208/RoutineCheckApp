package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 날짜별 메모 (v3). 홈 화면의 주간 달력에서 요일을 누르면 열리는 메모지.
 * 하루에 메모 1개. 내용을 모두 지우면 행 자체를 삭제합니다.
 */
@Entity(tableName = "daily_memos")
data class DailyMemoEntity(
    /** "yyyy-MM-dd" */
    @PrimaryKey
    val date: String,

    val content: String,

    val updatedAt: Long = System.currentTimeMillis()
)
