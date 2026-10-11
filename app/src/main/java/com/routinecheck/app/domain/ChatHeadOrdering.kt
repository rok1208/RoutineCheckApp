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

/** 챗헤드에 표시될 항목 하나 */
data class ChatHeadEntry(
    val packageName: String,
    val label: String,
    /** 이 앱이 속한 진행 중 루틴들 (실행 시 모두 체크됨). 비어 있으면 전용 목록 앱 */
    val routineIds: List<Long>,
    val routineName: String?,
    /** 루틴 앱이면 확인 여부, 전용 목록 앱이면 null */
    val isChecked: Boolean?
) {
    val isRoutineApp: Boolean get() = routineIds.isNotEmpty()
}

/**
 * 결정사항(2026-10-09): "전용 목록 + 루틴 우선"
 *   ① 진행 중(미완료) 루틴의 미확인 앱 → ② 진행 중 루틴의 확인된 앱 → ③ 챗헤드 전용 목록 앱
 *   같은 앱은 한 번만 표시 (여러 루틴에 걸쳐 있으면 routineIds에 모두 담김)
 */
object ChatHeadOrdering {

    fun build(
        activeRoutines: List<ActiveRoutineInput>,
        pinnedApps: List<Pair<String, String>>
    ): List<ChatHeadEntry> {
        val running = activeRoutines.filter { !it.isComplete }.sortedBy { it.startTime }

        // 패키지별로 소속 루틴 모으기 (등장 순서 유지)
        val order = LinkedHashMap<String, MutableList<ActiveRoutineInput>>()
        val labels = HashMap<String, String>()
        running.forEach { r ->
            r.apps.forEach { (pkg, label) ->
                order.getOrPut(pkg) { mutableListOf() }.add(r)
                labels.putIfAbsent(pkg, label)
            }
        }

        val routineEntries = order.map { (pkg, routines) ->
            ChatHeadEntry(
                packageName = pkg,
                label = labels[pkg] ?: pkg,
                routineIds = routines.map { it.routineId },
                routineName = routines.first().name,
                isChecked = routines.all { pkg in it.checkedPackages }
            )
        }
        val unchecked = routineEntries.filter { it.isChecked == false }
        val checked = routineEntries.filter { it.isChecked == true }

        val used = order.keys
        val pinned = pinnedApps
            .distinctBy { it.first }
            .filter { it.first !in used }
            .map { (pkg, label) -> ChatHeadEntry(pkg, label, emptyList(), null, null) }

        return unchecked + checked + pinned
    }
}
