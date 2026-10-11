package com.routinecheck.app.data.local.dao

import androidx.room.*
import com.routinecheck.app.data.local.entity.RoutineAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineAppDao {

    @Query("SELECT * FROM routine_apps WHERE routineId = :routineId ORDER BY sortOrder ASC")
    fun getAppsForRoutine(routineId: Long): Flow<List<RoutineAppEntity>>

    @Query("SELECT * FROM routine_apps WHERE routineId = :routineId ORDER BY sortOrder ASC")
    suspend fun getAppsForRoutineOnce(routineId: Long): List<RoutineAppEntity>

    /** 모든 루틴의 등록 앱 (홈/챗헤드/통계에서 루틴별로 묶어서 사용) */
    @Query("SELECT * FROM routine_apps ORDER BY routineId ASC, sortOrder ASC")
    fun observeAll(): Flow<List<RoutineAppEntity>>

    @Query("SELECT * FROM routine_apps ORDER BY routineId ASC, sortOrder ASC")
    suspend fun getAllOnce(): List<RoutineAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: RoutineAppEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<RoutineAppEntity>)

    @Delete
    suspend fun delete(app: RoutineAppEntity)

    @Query("DELETE FROM routine_apps WHERE routineId = :routineId")
    suspend fun deleteAllForRoutine(routineId: Long)
}
