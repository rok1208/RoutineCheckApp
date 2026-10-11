package com.routinecheck.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 주의: 아래 SQL은 각 Entity의 컬럼 정의와 정확히 일치해야 합니다.
 * (Entity를 고치면 SQL, AppDatabase.version, 새 Migration을 함께 고쳐야 앱이 시작됩니다.)
 */

/** v1 → v2: 챗헤드 설정 테이블 추가 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chathead_settings` (" +
                "`id` INTEGER NOT NULL, " +
                "`opacity` REAL NOT NULL, " +
                "`onCompleteBehavior` TEXT NOT NULL, " +
                "`iconUri` TEXT, " +
                "`edgeSide` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

/** v2 → v3: 날짜별 메모 테이블 추가 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_memos` (" +
                "`date` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`))"
        )
    }
}

/** v3 → v4: 챗헤드 핵심화 — 전용 앱 목록 테이블 + 설정 컬럼 2개 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `chathead_settings` ADD COLUMN `uiStyle` TEXT NOT NULL DEFAULT 'CAROUSEL'")
        db.execSQL("ALTER TABLE `chathead_settings` ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chathead_apps` (" +
                "`packageName` TEXT NOT NULL, " +
                "`appLabel` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`packageName`))"
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
