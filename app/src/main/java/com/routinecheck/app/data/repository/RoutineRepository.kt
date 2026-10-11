package com.routinecheck.app.data.repository

import androidx.room.withTransaction
import com.routinecheck.app.data.local.AppDatabase
import com.routinecheck.app.data.local.dao.ChecklistStateDao
import com.routinecheck.app.data.local.dao.RoutineAppDao
import com.routinecheck.app.data.local.dao.RoutineDao
import com.routinecheck.app.data.local.entity.RoutineAppEntity
import com.routinecheck.app.data.local.entity.RoutineChecklistStateEntity
import com.routinecheck.app.data.local.entity.RoutineEntity
import com.routinecheck.app.scheduler.RoutineAlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** 루틴 + 등록 앱 묶음 */
data class RoutineWithApps(
    val routine: RoutineEntity,
    val apps: List<RoutineAppEntity>
)

/**
 * 루틴 CRUD + 등록 앱 + 체크리스트 상태를 함께 다루는 Repository.
 * 루틴을 저장/삭제하면 정확 알람도 여기서 같이 등록/취소합니다 (화면 쪽에서 신경 쓸 필요 없음).
 */
@Singleton
class RoutineRepository @Inject constructor(
    private val db: AppDatabase,
    private val routineDao: RoutineDao,
    private val routineAppDao: RoutineAppDao,
    private val checklistStateDao: ChecklistStateDao,
    private val alarmScheduler: RoutineAlarmScheduler
) {
    fun getRoutines(): Flow<List<RoutineEntity>> = routineDao.getAllRoutines()

    fun observeAllApps(): Flow<List<RoutineAppEntity>> = routineAppDao.observeAll()

    fun observeRoutinesWithApps(): Flow<List<RoutineWithApps>> =
        combine(routineDao.getAllRoutines(), routineAppDao.observeAll()) { routines, apps ->
            val byRoutine = apps.groupBy { it.routineId }
            routines.map { RoutineWithApps(it, byRoutine[it.routineId].orEmpty()) }
        }

    suspend fun getRoutine(routineId: Long): RoutineEntity? = routineDao.getRoutineById(routineId)

    suspend fun getRoutineWithApps(routineId: Long): RoutineWithApps? {
        val routine = routineDao.getRoutineById(routineId) ?: return null
        return RoutineWithApps(routine, routineAppDao.getAppsForRoutineOnce(routineId))
    }

    suspend fun getAllOnce(): List<RoutineEntity> = routineDao.getAllOnce()

    /**
     * 루틴 생성/수정 + 등록 앱 전체 교체를 하나의 트랜잭션으로 저장합니다.
     * routine.routineId == 0 이면 새로 만들고, 아니면 수정합니다.
     * @return 저장된 routineId
     */
    suspend fun saveRoutine(routine: RoutineEntity, apps: List<RoutineAppEntity>): Long {
        val savedId = db.withTransaction {
            val id = if (routine.routineId == 0L) {
                routineDao.insert(routine.copy(createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            } else {
                routineDao.update(routine.copy(updatedAt = System.currentTimeMillis()))
                routine.routineId
            }
            routineAppDao.deleteAllForRoutine(id)
            routineAppDao.insertAll(
                apps.distinctBy { it.packageName }.mapIndexed { index, app ->
                    app.copy(id = 0, routineId = id, sortOrder = index)
                }
            )
            id
        }
        routineDao.getRoutineById(savedId)?.let { alarmScheduler.schedule(it) }
        return savedId
    }

    suspend fun setEnabled(routineId: Long, enabled: Boolean) {
        val routine = routineDao.getRoutineById(routineId) ?: return
        val updated = routine.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis())
        routineDao.update(updated)
        alarmScheduler.schedule(updated) // 비활성화면 내부에서 cancel
    }

    suspend fun deleteRoutine(routineId: Long) {
        alarmScheduler.cancel(routineId)
        routineDao.deleteById(routineId) // routine_apps, checklist는 FK CASCADE로 함께 삭제
    }

    /** 모든 루틴 알람을 다시 등록 (재부팅, 시간대 변경, 앱 업데이트 후) */
    suspend fun rescheduleAll() {
        routineDao.getAllOnce().forEach { alarmScheduler.schedule(it) }
    }

    fun getChecklistForDate(routineId: Long, date: String): Flow<List<RoutineChecklistStateEntity>> =
        checklistStateDao.getStateForDate(routineId, date)

    fun observeChecklistForDate(date: String): Flow<List<RoutineChecklistStateEntity>> =
        checklistStateDao.observeAllForDate(date)

    fun observeChecklistBetween(from: String, to: String): Flow<List<RoutineChecklistStateEntity>> =
        checklistStateDao.observeBetween(from, to)

    /** 체크 상태 저장 (없으면 만들고, 있으면 갱신) */
    suspend fun setChecked(routineId: Long, packageName: String, date: String, checked: Boolean) {
        val now = System.currentTimeMillis()
        val existing = checklistStateDao.getState(routineId, packageName, date)
        if (existing == null) {
            checklistStateDao.upsert(
                RoutineChecklistStateEntity(
                    routineId = routineId,
                    packageName = packageName,
                    date = date,
                    isChecked = checked,
                    checkedAt = if (checked) now else null
                )
            )
        } else {
            checklistStateDao.updateChecked(routineId, packageName, date, checked, if (checked) now else null)
        }
    }

    /** 챗헤드에서 앱을 열었을 때: 그 앱이 속한 진행 중 루틴 모두에 체크 */
    suspend fun markOpened(routineIds: List<Long>, packageName: String, date: String) {
        routineIds.forEach { setChecked(it, packageName, date, true) }
    }
}
