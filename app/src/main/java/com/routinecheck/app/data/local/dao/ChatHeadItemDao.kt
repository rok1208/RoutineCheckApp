package com.routinecheck.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.routinecheck.app.data.local.entity.ChatHeadItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHeadItemDao {

    /** 전체 항목 (폴더 구조는 Repository/도메인 로직에서 parentId로 조립) */
    @Query("SELECT * FROM chathead_items ORDER BY sortOrder ASC, addedAt ASC")
    fun observeAll(): Flow<List<ChatHeadItemEntity>>

    @Query("SELECT * FROM chathead_items ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getAllOnce(): List<ChatHeadItemEntity>

    @Query("SELECT * FROM chathead_items WHERE itemId = :itemId")
    suspend fun getById(itemId: Long): ChatHeadItemEntity?

    /** 한 폴더(또는 최상단 = null)의 바로 아래 항목. SQLite의 IS는 null도 비교 가능 */
    @Query("SELECT * FROM chathead_items WHERE parentId IS :parentId ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getChildren(parentId: Long?): List<ChatHeadItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM chathead_items WHERE parentId IS :parentId")
    suspend fun maxSortOrder(parentId: Long?): Int

    @Insert
    suspend fun insert(item: ChatHeadItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ChatHeadItemEntity>)

    @Update
    suspend fun update(item: ChatHeadItemEntity)

    @Update
    suspend fun updateAll(items: List<ChatHeadItemEntity>)

    @Query("DELETE FROM chathead_items WHERE itemId IN (:itemIds)")
    suspend fun deleteByIds(itemIds: List<Long>)

    /** 폴더 삭제: 안에 있던 항목은 한 단계 위로 올리고 폴더만 지움 */
    @Transaction
    suspend fun deleteFolderKeepChildren(folder: ChatHeadItemEntity) {
        val children = getChildren(folder.itemId)
        val base = maxSortOrder(folder.parentId) + 1
        updateAll(children.mapIndexed { i, c -> c.copy(parentId = folder.parentId, sortOrder = base + i) })
        deleteByIds(listOf(folder.itemId))
    }
}
