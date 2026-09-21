package com.routinecheck.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.routinecheck.app.data.local.dao.ChecklistStateDao
import com.routinecheck.app.data.local.dao.NotificationBadgeDao
import com.routinecheck.app.data.local.dao.RoutineAppDao
import com.routinecheck.app.data.local.dao.RoutineDao
import com.routinecheck.app.data.local.entity.AppNotificationBadgeEntity
import com.routinecheck.app.data.local.entity.RoutineAppEntity
import com.routinecheck.app.data.local.entity.RoutineChecklistStateEntity
import com.routinecheck.app.data.local.entity.RoutineEntity

/**
 * 앱 전체에서 딱 하나만 존재해야 하는 DB 인스턴스 정의.
 * 실제 생성은 di/DatabaseModule.kt 에서 합니다.
 *
 * 스키마를 바꿀 때는:
 *   1. 이 파일의 entities 목록에 새 Entity 추가/수정
 *   2. version 숫자를 올리기
 *   3. di/DatabaseModule.kt 의 databaseBuilder에 Migration 추가
 *   4. app/schemas/ 에 자동 생성된 JSON을 커밋 (exportSchema = true, 기본값)
 */
@Database(
    entities = [
        RoutineEntity::class,
        RoutineAppEntity::class,
        RoutineChecklistStateEntity::class,
        AppNotificationBadgeEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun routineAppDao(): RoutineAppDao
    abstract fun checklistStateDao(): ChecklistStateDao
    abstract fun notificationBadgeDao(): NotificationBadgeDao
}
