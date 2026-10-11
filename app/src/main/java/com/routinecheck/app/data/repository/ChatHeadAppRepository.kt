package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.dao.ChatHeadAppDao
import com.routinecheck.app.data.local.entity.ChatHeadAppEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 챗헤드 전용 앱 목록 (루틴과 무관하게 항상 챗헤드에 뜨는 앱) */
@Singleton
class ChatHeadAppRepository @Inject constructor(
    private val dao: ChatHeadAppDao
) {
    fun observeApps(): Flow<List<ChatHeadAppEntity>> = dao.observeAll()

    suspend fun getApps(): List<ChatHeadAppEntity> = dao.getAllOnce()

    /** 순서까지 포함해 목록 전체 저장 */
    suspend fun saveApps(apps: List<InstalledAppInfo>) {
        val existing = dao.getAllOnce().associateBy { it.packageName }
        dao.replaceAll(
            apps.distinctBy { it.packageName }.map {
                ChatHeadAppEntity(
                    packageName = it.packageName,
                    appLabel = it.label,
                    addedAt = existing[it.packageName]?.addedAt ?: System.currentTimeMillis()
                )
            }
        )
    }

    suspend fun remove(packageName: String) = dao.delete(packageName)

    /** delta = -1 이면 앞으로, +1 이면 뒤로 한 칸 */
    suspend fun move(packageName: String, delta: Int) {
        val list = dao.getAllOnce().toMutableList()
        val from = list.indexOfFirst { it.packageName == packageName }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        dao.replaceAll(list)
    }
}
