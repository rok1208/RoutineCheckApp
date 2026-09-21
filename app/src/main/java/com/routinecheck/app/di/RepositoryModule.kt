package com.routinecheck.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository는 대부분 @Inject constructor로 바로 주입 가능해서 별도 @Provides가
 * 필요 없습니다. 인터페이스-구현체를 분리하게 되면(예: RoutineRepository 인터페이스 +
 * RoutineRepositoryImpl) 여기에 @Binds 함수를 추가해주세요.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
