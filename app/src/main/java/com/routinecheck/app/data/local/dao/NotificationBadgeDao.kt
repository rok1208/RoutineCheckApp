package com.routinecheck.app.data.local.dao

import androidx.room.*
import com.routinecheck.app.data.local.entity.AppNotificationBadgeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationBadgeDao {

    @Query("SELECT * FROM app_notification_badges WHERE packageName = :packageName")
    fun observeBadge(packageName: String): Flow<AppNotificationBadgeEntity?>

    @Query("SELECT * FROM app_notification_badges WHERE packageName IN (:packageNames)")
    fun observeBadges(packageNames: List<String>): Flow<List<AppNotificationBadgeEntity>>

    @Query("SELECT * FROM app_notification_badges")
    fun observeAll(): Flow<List<AppNotificationBadgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(badge: AppNotificationBadgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(badges: List<AppNotificationBadgeEntity>)

    @Query("DELETE FROM app_notification_badges WHERE packageName = :packageName")
    suspend fun clear(packageName: String)

    @Query("DELETE FROM app_notification_badges")
    suspend fun clearAll()

    /** 알림 리스너가 다시 연결될 때 현재 알림 상태로 통째로 교체 */
    @Transaction
    suspend fun replaceAll(badges: List<AppNotificationBadgeEntity>) {
        clearAll()
        upsertAll(badges)
    }
}
