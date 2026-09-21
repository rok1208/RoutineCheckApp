package com.routinecheck.app.chathead

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * TODO(변공록): ③④ 챗헤드 오버레이 Foreground Service
 *   - WindowManager로 플로팅 뷰 추가/제거
 *   - 가장자리 스냅 + 가장자리→중앙 슬라이드 제스처로 재노출 (ChatHeadView.kt)
 *   - RoutineRepository.getChecklistForDate(), NotificationBadgeRepository.observeBadges()
 *     구독해서 팝업에 표시
 *   - AndroidManifest.xml 에 이미 foregroundServiceType="specialUse" 로 등록해둠
 */
class ChatHeadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
