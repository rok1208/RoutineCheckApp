package com.routinecheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
