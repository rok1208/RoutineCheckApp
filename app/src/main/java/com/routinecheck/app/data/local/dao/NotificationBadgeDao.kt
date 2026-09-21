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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(badge: AppNotificationBadgeEntity)

    @Query("DELETE FROM app_notification_badges WHERE packageName = :packageName")
    suspend fun clear(packageName: String)
}
