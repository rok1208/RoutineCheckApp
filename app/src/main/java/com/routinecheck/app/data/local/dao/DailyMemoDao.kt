package com.routinecheck.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.routinecheck.app.data.local.entity.DailyMemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMemoDao {

    /** 기간 안에서 메모가 있는 날짜만 (주간 달력의 메모 점 표시용) */
    @Query("SELECT date FROM daily_memos WHERE date BETWEEN :from AND :to")
    fun observeMemoDates(from: String, to: String): Flow<List<String>>

    @Query("SELECT * FROM daily_memos WHERE date = :date")
    suspend fun get(date: String): DailyMemoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memo: DailyMemoEntity)

    @Query("DELETE FROM daily_memos WHERE date = :date")
    suspend fun delete(date: String)
}
