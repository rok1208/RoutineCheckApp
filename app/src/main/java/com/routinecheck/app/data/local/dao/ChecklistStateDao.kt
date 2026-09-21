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

    @Query("""
        SELECT * FROM routine_checklist_state
        WHERE routineId = :routineId AND packageName = :packageName AND date = :date
        LIMIT 1
    """)
    suspend fun getState(routineId: Long, packageName: String, date: String): RoutineChecklistStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: RoutineChecklistStateEntity)

    /** 체크 토글 시 호출하는 편의 함수. Repository에서 조합해 씁니다. */
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
