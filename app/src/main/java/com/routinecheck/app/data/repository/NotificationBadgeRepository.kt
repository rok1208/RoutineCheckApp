package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.dao.NotificationBadgeDao
import com.routinecheck.app.data.local.entity.AppNotificationBadgeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 배지 표시용: urgent(전화·알람 등 중요한 알림)면 숫자 대신 "!" */
data class Badge(val count: Int, val urgent: Boolean) {
    val isVisible: Boolean get() = count > 0 || urgent
    val text: String get() = when {
        urgent -> "!"
        count > 99 -> "99+"
        else -> count.toString()
    }
}

/**
 * 앱별 미확인 알림 배지.
 * AppNotificationListenerService가 상태바 알림을 볼 때마다 앱별 개수를 다시 계산해 저장하고,
 * 챗헤드가 observeBadges()로 구독해 배지를 그립니다.
 */
@Singleton
class NotificationBadgeRepository @Inject constructor(
    private val dao: NotificationBadgeDao
) {
    fun observeBadge(packageName: String): Flow<AppNotificationBadgeEntity?> = dao.observeBadge(packageName)

    /** packageName → Badge (배지가 있는 앱만) */
    fun observeBadges(): Flow<Map<String, Badge>> = dao.observeAll().map { list ->
        list.associate { it.packageName to Badge(it.unreadCount, it.hasUrgentFlag) }
            .filterValues { it.isVisible }
    }

    suspend fun setBadge(packageName: String, count: Int, urgent: Boolean) {
        if (count <= 0 && !urgent) dao.clear(packageName)
        else dao.upsert(AppNotificationBadgeEntity(packageName, count, urgent, System.currentTimeMillis()))
    }

    suspend fun replaceAll(badges: Map<String, Badge>) {
        dao.replaceAll(
            badges.filterValues { it.isVisible }.map { (pkg, b) ->
                AppNotificationBadgeEntity(pkg, b.count, b.urgent, System.currentTimeMillis())
            }
        )
    }

    suspend fun clearBadge(packageName: String) = dao.clear(packageName)
}
