package com.routinecheck.app.ui.chatheaditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinecheck.app.data.repository.ChatHeadItemRepository
import com.routinecheck.app.data.repository.InstalledAppInfo
import com.routinecheck.app.data.repository.toPinned
import com.routinecheck.app.domain.ChatHeadKind
import com.routinecheck.app.domain.ChatHeadOrdering
import com.routinecheck.app.domain.LinkUtils
import com.routinecheck.app.domain.PinnedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 옮기기 대화상자의 목적지 한 줄 */
data class FolderDestination(val id: Long?, val label: String, val depth: Int)

data class ChatHeadItemsUiState(
    val loaded: Boolean = false,
    /** 최상단 → 현재 폴더 (비어 있으면 최상단) */
    val path: List<PinnedItem> = emptyList(),
    /** 현재 단계의 폴더 (화면에 먼저 나옴) */
    val folders: List<PinnedItem> = emptyList(),
    /** 현재 단계의 앱·웹사이트 */
    val others: List<PinnedItem> = emptyList(),
    /** 폴더 id → 바로 아래 항목 수 */
    val childCounts: Map<Long, Int> = emptyMap(),
    val all: List<PinnedItem> = emptyList()
) {
    val currentFolderId: Long? get() = path.lastOrNull()?.id
    val isEmpty: Boolean get() = folders.isEmpty() && others.isEmpty()

    /** 현재 단계에 들어 있는 앱 (앱 선택 창 초기 선택값) */
    val appsHere: List<InstalledAppInfo>
        get() = others.filter { it.kind == ChatHeadKind.APP && it.packageName != null }
            .map { InstalledAppInfo(it.packageName!!, it.title) }

    /** item을 옮길 수 있는 곳: 최상단 + 모든 폴더 (자기 자신·자기 하위 폴더·지금 있는 곳 제외) */
    fun destinationsFor(item: PinnedItem): List<FolderDestination> {
        val blocked = if (item.kind == ChatHeadKind.FOLDER)
            ChatHeadOrdering.descendants(all, item.id).map { it.id }.toSet() + item.id
        else emptySet()
        val result = mutableListOf<FolderDestination>()
        if (item.parentId != null) result += FolderDestination(null, "최상단", 0)
        fun walk(parent: Long?, depth: Int) {
            all.filter { it.parentId == parent && it.kind == ChatHeadKind.FOLDER }.sortedBy { it.sortOrder }.forEach { f ->
                if (f.id in blocked) return@forEach
                if (f.id != item.parentId) result += FolderDestination(f.id, f.title, depth + 1)
                walk(f.id, depth + 1)
            }
        }
        walk(null, 0)
        return result
    }
}

/**
 * 챗헤드 항목 관리: 앱 · 웹사이트 · 폴더.
 * 폴더를 눌러 들어가면 그 폴더 안에서 추가/정리하고, "이전으로"·"최상단으로"로 빠져나옵니다.
 */
@HiltViewModel
class ChatHeadItemsViewModel @Inject constructor(
    private val repository: ChatHeadItemRepository
) : ViewModel() {

    private val folderId = MutableStateFlow<Long?>(null)

    val state: StateFlow<ChatHeadItemsUiState> = combine(repository.observeItems(), folderId) { rows, current ->
        val all = rows.map { it.toPinned() }
        var path = ChatHeadOrdering.pathTo(all, current)
        // 보고 있던 폴더가 지워졌으면 최상단으로
        if (current != null && path.lastOrNull()?.id != current) path = emptyList()
        val here = path.lastOrNull()?.id
        val level = all.filter { it.parentId == here }.sortedBy { it.sortOrder }
        ChatHeadItemsUiState(
            loaded = true,
            path = path,
            folders = level.filter { it.kind == ChatHeadKind.FOLDER },
            others = level.filter { it.kind != ChatHeadKind.FOLDER },
            childCounts = all.groupingBy { it.parentId ?: -1L }.eachCount(),
            all = all
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatHeadItemsUiState())

    fun openFolder(id: Long) { folderId.value = id }
    fun openPath(id: Long?) { folderId.value = id }

    /** 한 단계 위로. 이미 최상단이면 false (화면을 닫아야 함) */
    fun back(): Boolean {
        val path = state.value.path
        if (path.isEmpty()) return false
        folderId.value = path.dropLast(1).lastOrNull()?.id
        return true
    }

    fun top() { folderId.value = null }

    fun setApps(apps: List<InstalledAppInfo>) = viewModelScope.launch {
        repository.setAppsInFolder(state.value.currentFolderId, apps)
    }

    /** @return 오류 문구. null이면 성공 */
    fun addLink(title: String, url: String): String? {
        val normalized = LinkUtils.normalize(url) ?: return "주소를 확인해 주세요 (예: naver.com)"
        val name = title.trim().ifEmpty { LinkUtils.defaultTitle(normalized) }
        viewModelScope.launch { repository.addLink(state.value.currentFolderId, name.take(30), normalized) }
        return null
    }

    fun editLink(itemId: Long, title: String, url: String): String? {
        val normalized = LinkUtils.normalize(url) ?: return "주소를 확인해 주세요 (예: naver.com)"
        val name = title.trim().ifEmpty { LinkUtils.defaultTitle(normalized) }
        viewModelScope.launch { repository.edit(itemId, name.take(30), normalized) }
        return null
    }

    fun createFolder(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "폴더 이름을 입력해 주세요"
        viewModelScope.launch { repository.createFolder(state.value.currentFolderId, trimmed.take(20)) }
        return null
    }

    fun renameFolder(itemId: Long, name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "폴더 이름을 입력해 주세요"
        viewModelScope.launch { repository.edit(itemId, trimmed.take(20)) }
        return null
    }

    fun moveTo(itemId: Long, parentId: Long?) = viewModelScope.launch { repository.moveTo(itemId, parentId) }
    fun move(itemId: Long, delta: Int) = viewModelScope.launch { repository.move(itemId, delta) }
    fun delete(itemId: Long) = viewModelScope.launch { repository.delete(itemId) }
}
