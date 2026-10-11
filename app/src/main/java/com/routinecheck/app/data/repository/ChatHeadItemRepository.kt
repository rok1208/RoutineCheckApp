package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.ChatHeadItemDao
import com.routinecheck.app.data.local.ChatHeadItemEntity
import com.routinecheck.app.domain.ChatHeadKind
import com.routinecheck.app.domain.ChatHeadOrdering
import com.routinecheck.app.domain.PinnedItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

fun ChatHeadItemEntity.toPinned() = PinnedItem(
    id = itemId,
    kind = when (type) {
        ChatHeadItemEntity.TYPE_FOLDER -> ChatHeadKind.FOLDER
        ChatHeadItemEntity.TYPE_LINK -> ChatHeadKind.LINK
        else -> ChatHeadKind.APP
    },
    title = title,
    packageName = packageName,
    url = url,
    parentId = parentId,
    sortOrder = sortOrder
)

/**
 * 챗헤드에 직접 넣는 항목(앱 · 웹사이트 · 폴더) 관리.
 * 폴더는 몇 단계든 넣을 수 있고, 폴더를 지우면 안의 항목은 한 단계 위로 올라갑니다.
 */
@Singleton
class ChatHeadItemRepository @Inject constructor(
    private val dao: ChatHeadItemDao
) {
    fun observeItems(): Flow<List<ChatHeadItemEntity>> = dao.observeAll()

    suspend fun getItems(): List<ChatHeadItemEntity> = dao.getAllOnce()

    /** 앱 선택 창 결과를 한 폴더(또는 최상단)에 반영: 새로 고른 앱은 뒤에 추가, 선택 해제한 앱은 그 폴더에서 제거 */
    suspend fun setAppsInFolder(parentId: Long?, apps: List<InstalledAppInfo>) {
        val current = dao.getChildren(parentId).filter { it.type == ChatHeadItemEntity.TYPE_APP }
        val wanted = apps.map { it.packageName }.toSet()
        val removed = current.filter { it.packageName !in wanted }.map { it.itemId }
        if (removed.isNotEmpty()) dao.deleteByIds(removed)
        val existing = current.mapNotNull { it.packageName }.toSet()
        var order = dao.maxSortOrder(parentId) + 1
        val added = apps.filter { it.packageName !in existing }.map {
            ChatHeadItemEntity(
                type = ChatHeadItemEntity.TYPE_APP,
                title = it.label,
                packageName = it.packageName,
                parentId = parentId,
                sortOrder = order++
            )
        }
        if (added.isNotEmpty()) dao.insertAll(added)
    }

    suspend fun addLink(parentId: Long?, title: String, url: String): Long =
        dao.insert(
            ChatHeadItemEntity(
                type = ChatHeadItemEntity.TYPE_LINK,
                title = title,
                url = url,
                parentId = parentId,
                sortOrder = dao.maxSortOrder(parentId) + 1
            )
        )

    suspend fun createFolder(parentId: Long?, name: String): Long =
        dao.insert(
            ChatHeadItemEntity(
                type = ChatHeadItemEntity.TYPE_FOLDER,
                title = name,
                parentId = parentId,
                sortOrder = dao.maxSortOrder(parentId) + 1
            )
        )

    /** 폴더 이름 / 웹사이트 이름·주소 변경 */
    suspend fun edit(itemId: Long, title: String, url: String? = null) {
        val item = dao.getById(itemId) ?: return
        dao.update(item.copy(title = title, url = if (item.type == ChatHeadItemEntity.TYPE_LINK) url ?: item.url else item.url))
    }

    /** 다른 폴더(또는 최상단)로 옮기기. 폴더를 자기 자신이나 자기 하위로 옮기는 것은 막습니다 */
    suspend fun moveTo(itemId: Long, newParentId: Long?) {
        val item = dao.getById(itemId) ?: return
        if (item.parentId == newParentId) return
        if (item.type == ChatHeadItemEntity.TYPE_FOLDER && newParentId != null) {
            val all = dao.getAllOnce().map { it.toPinned() }
            val blocked = ChatHeadOrdering.descendants(all, itemId).map { it.id }.toSet() + itemId
            if (newParentId in blocked) return
        }
        dao.update(item.copy(parentId = newParentId, sortOrder = dao.maxSortOrder(newParentId) + 1))
    }

    /**
     * 같은 폴더 안에서 한 칸 앞(-1)/뒤(+1)로. 화면에는 폴더가 먼저 나오므로
     * 폴더끼리, 앱·웹사이트끼리 순서를 바꿉니다.
     */
    suspend fun move(itemId: Long, delta: Int) {
        val item = dao.getById(itemId) ?: return
        val isFolder = item.type == ChatHeadItemEntity.TYPE_FOLDER
        val group = dao.getChildren(item.parentId)
            .filter { (it.type == ChatHeadItemEntity.TYPE_FOLDER) == isFolder }
            .toMutableList()
        val from = group.indexOfFirst { it.itemId == itemId }
        val to = (from + delta).coerceIn(0, group.lastIndex)
        if (from < 0 || from == to) return
        group.add(to, group.removeAt(from))
        dao.updateAll(group.mapIndexed { i, it -> it.copy(sortOrder = i) })
    }

    suspend fun delete(itemId: Long) {
        val item = dao.getById(itemId) ?: return
        if (item.type == ChatHeadItemEntity.TYPE_FOLDER) dao.deleteFolderKeepChildren(item)
        else dao.deleteByIds(listOf(itemId))
    }
}
