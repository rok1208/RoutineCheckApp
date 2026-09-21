package com.routinecheck.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * TODO(석광현): 메신저 등 등록된 앱의 알림을 감지해 NotificationBadgeRepository에 반영
 *
 * 참고: NotificationListenerService는 매니페스트에 별도 <service> + 퍼미션
 * (BIND_NOTIFICATION_LISTENER_SERVICE) 등록과, 사용자가 설정에서 별도로 "알림 접근 권한"을
 * 켜줘야 동작합니다. 이건 온보딩의 3개 권한과는 별개 플로우라 추후 논의가 필요해요.
 */
class AppNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        // TODO: sbn.packageName 이 루틴에 등록된 앱이면 NotificationBadgeRepository.reportNotification() 호출
    }
}
