package com.routinecheck.app.data.local.dao

import androidx.room.*
import com.routinecheck.app.data.local.entity.RoutineChecklistStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistStateDao {

    @Query("""
        SELECT * FROM routine_checklist_state
        WHERE routineId = :routineId AND date = :date
    """)
    fun getStateForDate(routineId: Long, date: String): Flow<List<RoutineChecklistStateEntity>>

    /** 특정 날짜의 모든 루틴 체크 상태 (홈 "오늘의 루틴", 챗헤드 캐러셀) */
    @Query("SELECT * FROM routine_checklist_state WHERE date = :date")
    fun observeAllForDate(date: String): Flow<List<RoutineChecklistStateEntity>>

    /** 기간 내 체크 상태 (통계 화면) */
    @Query("SELECT * FROM routine_checklist_state WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: String, to: String): Flow<List<RoutineChecklistStateEntity>>

    @Query("""
        SELECT * FROM routine_checklist_state
        WHERE routineId = :routineId AND packageName = :packageName AND date = :date
        LIMIT 1
    """)
    suspend fun getState(routineId: Long, packageName: String, date: String): RoutineChecklistStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: RoutineChecklistStateEntity)

    @Query("""
        UPDATE routine_checklist_state
        SET isChecked = :checked, checkedAt = :checkedAt
        WHERE routineId = :routineId AND packageName = :packageName AND date = :date
    """)
    suspend fun updateChecked(
        routineId: Long,
        packageName: String,
        date: String,
        checked: Boolean,
        checkedAt: Long?
    )
}
