package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.dao.NotificationBadgeDao
import com.routinecheck.app.data.local.entity.AppNotificationBadgeEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱별 미확인 알림 배지 상태 관리.
 * AppNotificationListenerService가 알림을 감지할 때마다 upsert 호출.
 * 챗헤드 확장 팝업(④)에서 observeBadges로 구독해 배지를 그립니다.
 * 담당: 석광현
 */
@Singleton
class NotificationBadgeRepository @Inject constructor(
    private val notificationBadgeDao: NotificationBadgeDao
) {
    fun observeBadge(packageName: String): Flow<AppNotificationBadgeEntity?> =
        notificationBadgeDao.observeBadge(packageName)

    fun observeBadges(packageNames: List<String>): Flow<List<AppNotificationBadgeEntity>> =
        notificationBadgeDao.observeBadges(packageNames)

    suspend fun reportNotification(packageName: String, isUrgent: Boolean = false) {
        // TODO(석광현): 실제로는 기존 count를 읽어서 +1 하는 로직이 필요합니다.
        //   지금은 자리만 잡아둔 스텁입니다.
        notificationBadgeDao.upsert(
            AppNotificationBadgeEntity(
                packageName = packageName,
                unreadCount = 1,
                hasUrgentFlag = isUrgent
            )
        )
    }

    suspend fun clearBadge(packageName: String) = notificationBadgeDao.clear(packageName)
}
