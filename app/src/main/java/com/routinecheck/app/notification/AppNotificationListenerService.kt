package com.routinecheck.app.notification

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.routinecheck.app.data.repository.Badge
import com.routinecheck.app.data.repository.NotificationBadgeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 상태바 알림을 읽어서 앱별 미확인 알림 수를 배지 테이블에 반영 (결정사항: 실제 알림 읽기).
 *
 * 사용자가 설정 > 알림 접근에서 이 앱을 켜야 동작합니다 (온보딩의 "권장" 항목).
 * 알림이 올라오거나 사라질 때마다 현재 떠 있는 알림 전체를 다시 세어서 통째로 교체하므로
 * 개수가 어긋나지 않습니다. (짧은 시간에 여러 개가 오면 0.3초 모아서 한 번만 계산)
 */
@AndroidEntryPoint
class AppNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var badgeRepository: NotificationBadgeRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSync: Job? = null
    @Volatile private var connected = false

    override fun onListenerConnected() {
        connected = true
        requestSync()
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = requestSync()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = requestSync()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun requestSync() {
        pendingSync?.cancel()
        pendingSync = scope.launch {
            delay(300)
            if (!connected) return@launch
            val active = runCatching { activeNotifications }.getOrNull() ?: return@launch
            badgeRepository.replaceAll(countBadges(active))
        }
    }

    private fun countBadges(active: Array<StatusBarNotification>): Map<String, Badge> =
        active
            .filter { it.packageName != packageName && it.isClearable && !it.isOngoing }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupBy { it.packageName }
            .mapValues { (_, list) ->
                Badge(
                    count = list.sumOf { if (it.notification.number > 0) it.notification.number else 1 },
                    urgent = list.any { sbn -> sbn.notification.category?.let { it in URGENT_CATEGORIES } == true }
                )
            }

    companion object {
        private val URGENT_CATEGORIES: Set<String> = buildSet {
            add(Notification.CATEGORY_CALL)
            add(Notification.CATEGORY_ALARM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(Notification.CATEGORY_MISSED_CALL)
        }
    }
}
