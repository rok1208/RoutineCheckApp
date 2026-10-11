package com.routinecheck.app.data.local

/*
 * Room 테이블 정의 (Entity) 모음 — 테이블 7개.
 *   routines · routine_apps · routine_checklist_state · daily_memos
 *   chathead_settings · chathead_items · app_notification_badges
 * 컬럼을 바꾸면 AppDatabase.version 과 Migrations.kt 를 함께 고쳐야 합니다.
 */

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─────────────────────────────── RoutineEntity

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

    /** v5: 시작 시각 알림을 보낼지 (선택). 꺼도 챗헤드 우선 표시·자동 펼침은 그대로 동작 */
    val notifyEnabled: Boolean = true,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────── RoutineAppEntity

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

// ─────────────────────────────── RoutineChecklistStateEntity

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

// ─────────────────────────────── DailyMemoEntity

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

// ─────────────────────────────── ChatHeadSettingsEntity

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
 *
 * v5: 루틴 알림 방식(alertMode) 컬럼 추가
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
    val enabled: Boolean = true,

    /** v5: 루틴 시작 알림 방식 "SOUND_VIBRATE"(기본) / "SOUND" / "VIBRATE" — 앱 전체 설정이라 이 표에 함께 둡니다 */
    val alertMode: String = "SOUND_VIBRATE"
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

// ─────────────────────────────── ChatHeadItemEntity

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

// ─────────────────────────────── AppNotificationBadgeEntity

/**
 * 설치된 앱별 미확인 알림 캐시. NotificationListenerService가 갱신합니다.
 * routine_apps 와는 packageName 값으로만 연결되는 느슨한 참조이며, FK가 아닙니다
 * (배지 테이블은 루틴에 등록되지 않은 앱까지 포함해 시스템 전체 알림을 캐시하기 때문).
 * 담당: 석광현
 */
@Entity(tableName = "app_notification_badges")
data class AppNotificationBadgeEntity(
    @PrimaryKey
    val packageName: String,

    val unreadCount: Int = 0,

    /** true면 숫자 대신 느낌표(❗)만 표시 */
    val hasUrgentFlag: Boolean = false,

    val lastUpdatedAt: Long = System.currentTimeMillis()
)
