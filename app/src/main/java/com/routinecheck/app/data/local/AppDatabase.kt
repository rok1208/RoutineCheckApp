package com.routinecheck.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.routinecheck.app.data.local.dao.ChatHeadItemDao
import com.routinecheck.app.data.local.dao.ChatHeadSettingsDao
import com.routinecheck.app.data.local.dao.ChecklistStateDao
import com.routinecheck.app.data.local.dao.DailyMemoDao
import com.routinecheck.app.data.local.dao.NotificationBadgeDao
import com.routinecheck.app.data.local.dao.RoutineAppDao
import com.routinecheck.app.data.local.dao.RoutineDao
import com.routinecheck.app.data.local.entity.AppNotificationBadgeEntity
import com.routinecheck.app.data.local.entity.ChatHeadItemEntity
import com.routinecheck.app.data.local.entity.ChatHeadSettingsEntity
import com.routinecheck.app.data.local.entity.DailyMemoEntity
import com.routinecheck.app.data.local.entity.RoutineAppEntity
import com.routinecheck.app.data.local.entity.RoutineChecklistStateEntity
import com.routinecheck.app.data.local.entity.RoutineEntity

/**
 * 앱 전체에서 딱 하나만 존재해야 하는 DB 인스턴스 정의.
 * 실제 생성은 di/DatabaseModule.kt 에서 합니다.
 *
 * 버전 이력
 *  v1: routines, routine_apps, routine_checklist_state, app_notification_badges
 *  v2: + chathead_settings
 *  v3: + daily_memos
 *  v4: + chathead_apps, chathead_settings.uiStyle / enabled
 *  v5: chathead_apps → chathead_items (앱 · 웹사이트 · 폴더 통합, 폴더 안에 넣기 가능)
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
    version = 5,
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
