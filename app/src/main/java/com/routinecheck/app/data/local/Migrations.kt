package com.routinecheck.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DB 버전 1 → 2: 챗헤드 설정 테이블(chathead_settings) 추가.
 * 기존 테이블/데이터는 건드리지 않습니다.
 *
 * 주의: 아래 CREATE TABLE 문은 ChatHeadSettingsEntity의 컬럼 정의와 정확히 일치해야 합니다.
 * (Entity를 고치면 이 SQL과 version, 새 Migration도 함께 고쳐야 앱이 시작됩니다.)
 */
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
