package com.routinecheck.app.data.local

import androidx.room.Room
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** DB/DAO 제공 모듈. 새 DAO를 추가하면 여기에도 provide 함수를 추가해주세요. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "routine_check.db")
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideRoutineAppDao(db: AppDatabase): RoutineAppDao = db.routineAppDao()

    @Provides
    fun provideChecklistStateDao(db: AppDatabase): ChecklistStateDao = db.checklistStateDao()

    @Provides
    fun provideNotificationBadgeDao(db: AppDatabase): NotificationBadgeDao = db.notificationBadgeDao()

    @Provides
    fun provideChatHeadSettingsDao(db: AppDatabase): ChatHeadSettingsDao = db.chatHeadSettingsDao()

    @Provides
    fun provideDailyMemoDao(db: AppDatabase): DailyMemoDao = db.dailyMemoDao()

    @Provides
    fun provideChatHeadItemDao(db: AppDatabase): ChatHeadItemDao = db.chatHeadItemDao()
}
