package com.routinecheck.app.data.local

/*
 * Room DAO 모음 — 테이블마다 하나씩 (SQL은 여기에만 있습니다).
 * Repository(data/repository)만 DAO를 사용하고, 화면 코드는 DAO를 직접 쓰지 않습니다.
 */

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────── RoutineDao

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routines ORDER BY startTime ASC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY startTime ASC")
    suspend fun getAllOnce(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE routineId = :routineId")
    suspend fun getRoutineById(routineId: Long): RoutineEntity?

    @Query("SELECT * FROM routines WHERE routineId = :routineId")
    fun observeRoutineById(routineId: Long): Flow<RoutineEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE routineId = :routineId")
    suspend fun deleteById(routineId: Long)
}

// ─────────────────────────────── RoutineAppDao

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

// ─────────────────────────────── ChecklistStateDao

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

// ─────────────────────────────── DailyMemoDao

@Dao
interface DailyMemoDao {

    /** 기간 안에서 메모가 있는 날짜만 (주간 달력의 메모 점 표시용) */
    @Query("SELECT date FROM daily_memos WHERE date BETWEEN :from AND :to")
    fun observeMemoDates(from: String, to: String): Flow<List<String>>

    @Query("SELECT * FROM daily_memos WHERE date = :date")
    suspend fun get(date: String): DailyMemoEntity?

    /** 한 날짜의 메모를 실시간으로 (다른 화면에서 고친 내용도 바로 반영) */
    @Query("SELECT * FROM daily_memos WHERE date = :date")
    fun observe(date: String): Flow<DailyMemoEntity?>

    /** 전체 메모 화면: 최근 날짜부터 */
    @Query("SELECT * FROM daily_memos ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyMemoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memo: DailyMemoEntity)

    @Query("DELETE FROM daily_memos WHERE date = :date")
    suspend fun delete(date: String)
}

// ─────────────────────────────── ChatHeadSettingsDao

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

// ─────────────────────────────── ChatHeadItemDao

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

// ─────────────────────────────── NotificationBadgeDao

@Dao
interface NotificationBadgeDao {

    @Query("SELECT * FROM app_notification_badges WHERE packageName = :packageName")
    fun observeBadge(packageName: String): Flow<AppNotificationBadgeEntity?>

    @Query("SELECT * FROM app_notification_badges WHERE packageName IN (:packageNames)")
    fun observeBadges(packageNames: List<String>): Flow<List<AppNotificationBadgeEntity>>

    @Query("SELECT * FROM app_notification_badges")
    fun observeAll(): Flow<List<AppNotificationBadgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(badge: AppNotificationBadgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(badges: List<AppNotificationBadgeEntity>)

    @Query("DELETE FROM app_notification_badges WHERE packageName = :packageName")
    suspend fun clear(packageName: String)

    @Query("DELETE FROM app_notification_badges")
    suspend fun clearAll()

    /** 알림 리스너가 다시 연결될 때 현재 알림 상태로 통째로 교체 */
    @Transaction
    suspend fun replaceAll(badges: List<AppNotificationBadgeEntity>) {
        clearAll()
        upsertAll(badges)
    }
}
