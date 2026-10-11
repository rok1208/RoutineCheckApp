package com.routinecheck.app.chathead

import androidx.compose.ui.graphics.ImageBitmap
import com.routinecheck.app.data.repository.Badge
import com.routinecheck.app.data.repository.ChatHeadSettings
import com.routinecheck.app.data.repository.EdgeSide
import com.routinecheck.app.domain.ChatHeadEntry
import com.routinecheck.app.domain.ChatHeadKind

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

/** 지금 열어 둔 폴더 경로의 한 칸 (최상단 → … → 현재 폴더) */
data class FolderCrumb(val id: Long, val title: String)

data class OverlayState(
    val mode: HeadMode = HeadMode.BUBBLE,
    val side: EdgeSide = EdgeSide.RIGHT,
    /** 확장 화면을 배치할 기준: 원형 아이콘 중심의 화면 y(px) */
    val anchorY: Int = 0,
    /** 현재 단계(최상단 또는 열어 둔 폴더)에 보이는 항목 */
    val entries: List<ChatHeadEntry> = emptyList(),
    /** 비어 있으면 최상단 */
    val folderPath: List<FolderCrumb> = emptyList(),
    /** 챗헤드에 들어 있는 모든 앱(루틴 앱 + 폴더 안의 앱까지) — 원형 아이콘 배지 합계용 */
    val allPackages: Set<String> = emptySet(),
    val badges: Map<String, Badge> = emptyMap(),
    val settings: ChatHeadSettings = ChatHeadSettings(),
    val progress: RoutineProgress? = null,
    val customIcon: ImageBitmap? = null,
    /** 드래그 중 제거 영역 위에 있는지 */
    val overTrash: Boolean = false
) {
    val inFolder: Boolean get() = folderPath.isNotEmpty()

    /** 챗헤드에 들어 있는 앱들의 미확인 알림 합계 */
    val totalBadge: Badge? get() = sumBadges(allPackages)

    /** 카드 하나의 배지: 앱은 그 앱, 폴더는 안에 든 앱들의 합계, 웹사이트는 없음 */
    fun badgeFor(entry: ChatHeadEntry): Badge? = when (entry.kind) {
        ChatHeadKind.APP -> entry.packageName?.let { badges[it] }
        ChatHeadKind.FOLDER -> sumBadges(entry.folderPackages)
        ChatHeadKind.LINK -> null
    }

    private fun sumBadges(packages: Collection<String>): Badge? {
        val relevant = packages.mapNotNull { badges[it] }
        if (relevant.isEmpty()) return null
        return Badge(relevant.sumOf { it.count }, relevant.any { it.urgent })
    }
}
