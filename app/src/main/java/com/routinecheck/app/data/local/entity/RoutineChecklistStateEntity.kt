package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 특정 날짜에 루틴의 특정 앱을 확인/완료했는지 상태.
 * 매일 새로운 date row가 쌓이는 구조입니다 (자정 기준 초기화 효과).
 * 담당: 변공록 (챗헤드 완료 처리)
 */
@Entity(
    tableName = "routine_checklist_state",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["routineId"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["routineId", "packageName", "date"], unique = true)
    ]
)
data class RoutineChecklistStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val routineId: Long,

    val packageName: String,

    /** "yyyy-MM-dd" */
    val date: String,

    val isChecked: Boolean = false,

    val checkedAt: Long? = null
)
