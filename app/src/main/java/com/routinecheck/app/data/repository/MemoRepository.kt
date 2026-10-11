package com.routinecheck.app.data.repository

import com.routinecheck.app.data.local.DailyMemoDao
import com.routinecheck.app.data.local.DailyMemoEntity
import com.routinecheck.app.domain.DateKey
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MemoRepository @Inject constructor(
    private val dao: DailyMemoDao
) {
    /** 기간 안에서 메모가 있는 날짜 집합 */
    fun observeMemoDates(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>> =
        dao.observeMemoDates(DateKey.of(from), DateKey.of(to)).map { keys -> keys.map(DateKey::parse).toSet() }

    suspend fun getMemo(date: LocalDate): String = dao.get(DateKey.of(date))?.content.orEmpty()

    fun observeMemo(date: LocalDate): Flow<String> = dao.observe(DateKey.of(date)).map { it?.content.orEmpty() }

    /** 작성한 모든 메모 (최근 날짜부터) */
    fun observeAll(): Flow<List<DailyMemoEntity>> = dao.observeAll()

    suspend fun deleteMemo(date: LocalDate) = dao.delete(DateKey.of(date))

    /** 내용이 비어 있으면 메모를 삭제합니다 */
    suspend fun saveMemo(date: LocalDate, content: String) {
        val key = DateKey.of(date)
        if (content.isBlank()) dao.delete(key)
        else dao.upsert(DailyMemoEntity(date = key, content = content, updatedAt = System.currentTimeMillis()))
    }
}
