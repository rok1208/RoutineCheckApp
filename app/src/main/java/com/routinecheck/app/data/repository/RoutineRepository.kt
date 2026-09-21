package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.dao.ChecklistStateDao
import com.routinecheck.app.data.local.dao.RoutineAppDao
import com.routinecheck.app.data.local.dao.RoutineDao
import com.routinecheck.app.data.local.entity.RoutineAppEntity
import com.routinecheck.app.data.local.entity.RoutineChecklistStateEntity
import com.routinecheck.app.data.local.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 루틴 CRUD + 등록 앱 + 체크리스트 상태를 함께 다루는 Repository.
 * ViewModel은 이 클래스만 알면 되고, DAO/Room을 직접 알 필요가 없습니다.
 *
 * 담당: 이현규 (루틴/앱 등록), 변공록 (챗헤드에서 체크 상태 갱신 시 사용)
 */
@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineAppDao: RoutineAppDao,
    private val checklistStateDao: ChecklistStateDao
) {
    fun getRoutines(): Flow<List<RoutineEntity>> = routineDao.getAllRoutines()

    fun observeRoutine(routineId: Long): Flow<RoutineEntity?> =
        routineDao.observeRoutineById(routineId)

    fun getAppsForRoutine(routineId: Long): Flow<List<RoutineAppEntity>> =
        routineAppDao.getAppsForRoutine(routineId)

    /** 루틴 생성 + 등록 앱을 한 번에 저장 (③ 앱 등록 화면에서 "저장" 눌렀을 때 사용) */
    suspend fun createRoutine(routine: RoutineEntity, apps: List<RoutineAppEntity>): Long {
        val routineId = routineDao.insert(routine)
        routineAppDao.insertAll(apps.map { it.copy(routineId = routineId) })
        return routineId
    }

    suspend fun updateRoutine(routine: RoutineEntity) = routineDao.update(routine)

    suspend fun deleteRoutine(routine: RoutineEntity) = routineDao.delete(routine)

    fun getChecklistForDate(routineId: Long, date: String): Flow<List<RoutineChecklistStateEntity>> =
        checklistStateDao.getStateForDate(routineId, date)

    /** 챗헤드에서 항목 체크할 때 호출 (없으면 만들고, 있으면 갱신) */
    suspend fun setChecked(routineId: Long, packageName: String, date: String, checked: Boolean) {
        val existing = checklistStateDao.getState(routineId, packageName, date)
        if (existing == null) {
            checklistStateDao.upsert(
                RoutineChecklistStateEntity(
                    routineId = routineId,
                    packageName = packageName,
                    date = date,
                    isChecked = checked,
                    checkedAt = if (checked) System.currentTimeMillis() else null
                )
            )
        } else {
            checklistStateDao.updateChecked(
                routineId, packageName, date, checked,
                if (checked) System.currentTimeMillis() else null
            )
        }
    }
}
