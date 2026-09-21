package com.routinecheck.app.scheduler

import android.content.Context
import com.routinecheck.app.data.local.entity.RoutineEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO(석광현): AlarmManager/WorkManager로 루틴 스케줄 등록/취소
 *   - daysOfWeek 비트마스크를 풀어서 요일별로 다음 알람 시각 계산
 *   - setExactAndAllowWhileIdle() 사용 시 SCHEDULE_EXACT_ALARM 권한 체크 필요 (Android 12+)
 *   - 루틴 저장/수정/삭제 시 RoutineRepository와 함께 호출되도록 연결
 */
@Singleton
class RoutineAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(routine: RoutineEntity) {
        // TODO: AlarmManager.setExactAndAllowWhileIdle(...) 구현
    }

    fun cancel(routineId: Long) {
        // TODO: 등록된 PendingIntent 취소
    }
}
