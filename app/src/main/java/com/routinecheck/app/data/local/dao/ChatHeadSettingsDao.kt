package com.routinecheck.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.routinecheck.app.data.local.entity.ChatHeadSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHeadSettingsDao {

    /** 설정을 실시간으로 관찰. 아직 저장된 적이 없으면 null (Repository가 기본값으로 대체) */
    @Query("SELECT * FROM chathead_settings WHERE id = 1")
    fun observe(): Flow<ChatHeadSettingsEntity?>

    /** 설정을 1회 조회 */
    @Query("SELECT * FROM chathead_settings WHERE id = 1")
    suspend fun getOnce(): ChatHeadSettingsEntity?

    /** 저장. id가 항상 1이라 이미 있으면 덮어씁니다. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: ChatHeadSettingsEntity)
}
