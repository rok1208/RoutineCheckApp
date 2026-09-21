package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 루틴에 등록된 앱 한 개(예: "기상 루틴"에 등록된 "캘린더").
 * 담당: 이현규 (앱 등록 화면), 변공록 (챗헤드 체크리스트)에서 함께 참조합니다.
 */
@Entity(
    tableName = "routine_apps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["routineId"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["routineId", "packageName"], unique = true),
        Index(value = ["routineId"])
    ]
)
data class RoutineAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val routineId: Long,

    val packageName: String,

    /** 설치 앱 목록 조회 시점의 표시 이름 캐시 */
    val appLabel: String,

    /** 챗헤드 체크리스트에서 보여줄 순서 */
    val sortOrder: Int = 0,

    /** 메신저처럼 알림 배지를 함께 보여줄 앱인지 여부 */
    val showsNotificationBadge: Boolean = false
)
