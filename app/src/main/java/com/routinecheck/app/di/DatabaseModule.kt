package com.routinecheck.app.di

import android.content.Context
import androidx.room.Room
import com.routinecheck.app.data.local.AppDatabase
import com.routinecheck.app.data.local.dao.ChecklistStateDao
import com.routinecheck.app.data.local.dao.NotificationBadgeDao
import com.routinecheck.app.data.local.dao.RoutineAppDao
import com.routinecheck.app.data.local.dao.RoutineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DB/DAO 제공 모듈. 새 DAO를 추가하면 여기에도 provide 함수를 추가해주세요.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "routine_check.db"
        )
            // TODO: 스키마 변경 시 .addMigrations(MIGRATION_1_2, ...) 추가
            .build()

    @Provides
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideRoutineAppDao(db: AppDatabase): RoutineAppDao = db.routineAppDao()

    @Provides
    fun provideChecklistStateDao(db: AppDatabase): ChecklistStateDao = db.checklistStateDao()

    @Provides
    fun provideNotificationBadgeDao(db: AppDatabase): NotificationBadgeDao = db.notificationBadgeDao()
}
