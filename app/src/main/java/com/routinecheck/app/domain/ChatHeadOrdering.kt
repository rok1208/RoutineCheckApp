package com.routinecheck.app.domain

/** 캐러셀/컨테이너 계산용 입력: 오늘 진행 중인 루틴 하나 */
data class ActiveRoutineInput(
    val routineId: Long,
    val name: String,
    val startTime: String,
    /** (packageName, label) — 루틴에 등록된 순서 */
    val apps: List<Pair<String, String>>,
    val checkedPackages: Set<String>
) {
    val isComplete: Boolean get() = apps.isNotEmpty() && apps.all { it.first in checkedPackages }
}

enum class ChatHeadKind { APP, LINK, FOLDER }

/** 사용자가 챗헤드에 직접 넣은 항목 (DB의 chathead_items 한 행과 같은 내용) */
data class PinnedItem(
    val id: Long,
    val kind: ChatHeadKind,
    val title: String,
    val packageName: String? = null,
    val url: String? = null,
    val parentId: Long? = null,
    val sortOrder: Int = 0
)

/** 챗헤드 화면에 그려지는 카드 하나 */
data class ChatHeadEntry(
    /** LazyColumn key 용 고유 문자열 */
    val key: String,
    val kind: ChatHeadKind,
    val label: String,
    val packageName: String? = null,
    val url: String? = null,
    /** 직접 넣은 항목이면 그 itemId, 루틴 앱이면 null */
    val itemId: Long? = null,
    /** 이 앱이 속한 진행 중 루틴들 (실행 시 모두 체크됨). 비어 있으면 루틴 앱이 아님 */
    val routineIds: List<Long> = emptyList(),
    val routineName: String? = null,
    /** 루틴 앱이면 확인 여부, 그 외에는 null */
    val isChecked: Boolean? = null,
    /** 폴더: 바로 아래 항목 수 */
    val childCount: Int = 0,
    /** 폴더: 안에 (하위 폴더까지) 들어 있는 앱 패키지 — 폴더 배지 합계용 */
    val folderPackages: List<String> = emptyList()
) {
    val isRoutineApp: Boolean get() = routineIds.isNotEmpty()
}

/**
 * 챗헤드 표시 순서 (순수 로직 — 단위 테스트 대상).
 *
 * 최상단: ① 진행 중 루틴의 확인 전 앱 → ② 확인한 루틴 앱 → ③ 폴더 → ④ 앱·웹사이트 (sortOrder 순)
 *         루틴에 이미 나온 앱은 ④에서 빠짐
 * 폴더 안: 하위 폴더 → 앱·웹사이트 (루틴 앱은 섞지 않음)
 */
object ChatHeadOrdering {

    fun build(
        activeRoutines: List<ActiveRoutineInput>,
        items: List<PinnedItem>,
        folderId: Long? = null
    ): List<ChatHeadEntry> {
        val routineEntries = if (folderId == null) routineEntries(activeRoutines) else emptyList()
        val routinePackages = routineEntries.mapNotNull { it.packageName }.toSet()

        val level = items.filter { it.parentId == folderId }.sortedBy { it.sortOrder }
        val folders = level.filter { it.kind == ChatHeadKind.FOLDER }.map { folder ->
            ChatHeadEntry(
                key = "item:${folder.id}",
                kind = ChatHeadKind.FOLDER,
                label = folder.title,
                itemId = folder.id,
                childCount = items.count { it.parentId == folder.id },
                folderPackages = descendants(items, folder.id).mapNotNull { it.packageName }.distinct()
            )
        }
        val others = level
            .filter { it.kind != ChatHeadKind.FOLDER }
            .filterNot { it.kind == ChatHeadKind.APP && folderId == null && it.packageName in routinePackages }
            .distinctBy { if (it.kind == ChatHeadKind.APP) "app:${it.packageName}" else "item:${it.id}" }
            .map {
                ChatHeadEntry(
                    key = "item:${it.id}",
                    kind = it.kind,
                    label = it.title,
                    packageName = it.packageName,
                    url = it.url,
                    itemId = it.id
                )
            }
        return routineEntries + folders + others
    }

    private fun routineEntries(activeRoutines: List<ActiveRoutineInput>): List<ChatHeadEntry> {
        val running = activeRoutines.filter { !it.isComplete }.sortedBy { it.startTime }
        val order = LinkedHashMap<String, MutableList<ActiveRoutineInput>>()
        val labels = HashMap<String, String>()
        running.forEach { r ->
            r.apps.forEach { (pkg, label) ->
                order.getOrPut(pkg) { mutableListOf() }.add(r)
                labels.putIfAbsent(pkg, label)
            }
        }
        val all = order.map { (pkg, routines) ->
            ChatHeadEntry(
                key = "routine:$pkg",
                kind = ChatHeadKind.APP,
                label = labels[pkg] ?: pkg,
                packageName = pkg,
                routineIds = routines.map { it.routineId },
                routineName = routines.first().name,
                isChecked = routines.all { pkg in it.checkedPackages }
            )
        }
        return all.filter { it.isChecked == false } + all.filter { it.isChecked == true }
    }

    /** 폴더 아래 모든 항목 (하위 폴더 포함, 순환 참조가 있어도 멈춤) */
    fun descendants(items: List<PinnedItem>, folderId: Long): List<PinnedItem> {
        val result = mutableListOf<PinnedItem>()
        val visited = mutableSetOf(folderId)
        var frontier = listOf(folderId)
        while (frontier.isNotEmpty()) {
            val children = items.filter { it.parentId in frontier }
            result += children
            frontier = children.filter { it.kind == ChatHeadKind.FOLDER && visited.add(it.id) }.map { it.id }
        }
        return result
    }

    /** 최상단 → 해당 폴더까지의 경로 (화면 위 "업무 / 개발" 표시용). 없는 폴더면 빈 목록 */
    fun pathTo(items: List<PinnedItem>, folderId: Long?): List<PinnedItem> {
        val byId = items.associateBy { it.id }
        val path = ArrayDeque<PinnedItem>()
        var current = folderId?.let { byId[it] }
        val seen = mutableSetOf<Long>()
        while (current != null && seen.add(current.id)) {
            path.addFirst(current)
            current = current.parentId?.let { byId[it] }
        }
        return path.toList()
    }
}
