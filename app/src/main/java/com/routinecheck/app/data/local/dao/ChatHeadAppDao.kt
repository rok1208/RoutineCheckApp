package com.routinecheck.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.routinecheck.app.data.local.entity.ChatHeadAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHeadAppDao {

    @Query("SELECT * FROM chathead_apps ORDER BY sortOrder ASC, addedAt ASC")
    fun observeAll(): Flow<List<ChatHeadAppEntity>>

    @Query("SELECT * FROM chathead_apps ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getAllOnce(): List<ChatHeadAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<ChatHeadAppEntity>)

    @Query("DELETE FROM chathead_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM chathead_apps")
    suspend fun deleteAll()

    /** 목록 전체를 주어진 순서대로 교체 (순서 변경/추가/삭제를 한 번에 저장) */
    @Transaction
    suspend fun replaceAll(apps: List<ChatHeadAppEntity>) {
        deleteAll()
        insertAll(apps.mapIndexed { index, app -> app.copy(sortOrder = index) })
    }
}
