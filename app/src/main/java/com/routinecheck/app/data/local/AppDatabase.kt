package com.routinecheck.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 앱 전체에서 딱 하나만 존재해야 하는 DB 인스턴스 정의.
 * 실제 생성은 data/local/DatabaseModule.kt 에서 합니다.
 *
 * 버전 이력
 *  v1: routines, routine_apps, routine_checklist_state, app_notification_badges
 *  v2: + chathead_settings
 *  v3: + daily_memos
 *  v4: + chathead_apps, chathead_settings.uiStyle / enabled
 *  v5: chathead_apps → chathead_items (앱 · 웹사이트 · 폴더 통합, 폴더 안에 넣기 가능),
 *      routines.notifyEnabled (시작 알림 선택), chathead_settings.alertMode (소리/진동)
 *  v6: routines.message (실행 시 화면 메시지), routines.date (날짜 지정 일정)
 *
 * 스키마를 바꿀 때는 entities / version / Migrations.kt / DatabaseModule 를 함께 고쳐주세요.
 */
@Database(
    entities = [
        RoutineEntity::class,
        RoutineAppEntity::class,
        RoutineChecklistStateEntity::class,
        AppNotificationBadgeEntity::class,
        ChatHeadSettingsEntity::class,
        DailyMemoEntity::class,
        ChatHeadItemEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun routineAppDao(): RoutineAppDao
    abstract fun checklistStateDao(): ChecklistStateDao
    abstract fun notificationBadgeDao(): NotificationBadgeDao
    abstract fun chatHeadSettingsDao(): ChatHeadSettingsDao
    abstract fun dailyMemoDao(): DailyMemoDao
    abstract fun chatHeadItemDao(): ChatHeadItemDao
}
