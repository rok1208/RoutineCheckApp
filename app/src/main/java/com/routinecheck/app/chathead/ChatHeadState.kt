package com.routinecheck.app.chathead

import androidx.compose.ui.graphics.ImageBitmap
import com.routinecheck.app.data.repository.Badge
import com.routinecheck.app.data.repository.ChatHeadSettings
import com.routinecheck.app.data.repository.EdgeSide
import com.routinecheck.app.domain.ChatHeadEntry

/** 챗헤드의 상태 4가지 (노션 화면 설계 ③~⑥) */
enum class HeadMode {
    /** ③ 기본: 화면 위 원형 아이콘 */
    BUBBLE,
    /** ④ 축소: 가장자리 얇은 바 */
    BAR,
    /** ⑤⑥ 확장: 휠 캐러셀 또는 컨테이너 */
    EXPANDED
}

/** 진행 중 루틴 요약 (확장 화면 머리말, 원형 아이콘 진행 링) */
data class RoutineProgress(val name: String, val done: Int, val total: Int, val extraCount: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val title: String get() = if (extraCount > 0) "$name 외 ${extraCount}개" else name
}

data class OverlayState(
    val mode: HeadMode = HeadMode.BUBBLE,
    val side: EdgeSide = EdgeSide.RIGHT,
    /** 확장 화면을 배치할 기준: 원형 아이콘 중심의 화면 y(px) */
    val anchorY: Int = 0,
    val entries: List<ChatHeadEntry> = emptyList(),
    val badges: Map<String, Badge> = emptyMap(),
    val settings: ChatHeadSettings = ChatHeadSettings(),
    val progress: RoutineProgress? = null,
    val customIcon: ImageBitmap? = null,
    /** 드래그 중 제거 영역 위에 있는지 */
    val overTrash: Boolean = false
) {
    /** 챗헤드에 올라온 앱들의 미확인 알림 합계 */
    val totalBadge: Badge? get() {
        val relevant = entries.mapNotNull { badges[it.packageName] }
        if (relevant.isEmpty()) return null
        return Badge(relevant.sumOf { it.count }, relevant.any { it.urgent })
    }
}
