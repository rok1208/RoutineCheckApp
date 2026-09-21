package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 루틴 하나(예: "기상 루틴")를 나타내는 테이블.
 * 담당: 석광현 (알림/스케줄), 이현규 (루틴 CRUD 화면)에서 함께 참조합니다.
 *
 * daysOfWeek 비트마스크: 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64
 * 예) 월/수/금 = 1 + 4 + 16 = 21
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val routineId: Long = 0,

    val name: String,

    /** "HH:mm" 형식, 예: "07:00" */
    val startTime: String,

    val daysOfWeek: Int,

    val alarmLinked: Boolean = false,

    val autoStart: Boolean = false,

    /** null이면 스누즈 미사용 */
    val snoozeMinutes: Int? = null,

    val isEnabled: Boolean = true,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)
