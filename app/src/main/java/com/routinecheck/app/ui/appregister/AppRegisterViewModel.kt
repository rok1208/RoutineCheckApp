package com.routinecheck.app.ui.appregister

import androidx.lifecycle.ViewModel
import com.routinecheck.app.data.repository.AppRegistryRepository
import com.routinecheck.app.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppRegisterViewModel @Inject constructor(
    private val appRegistryRepository: AppRegistryRepository,
    private val routineRepository: RoutineRepository
) : ViewModel() {
    // TODO(이현규): 설치 앱 목록 로드 + 선택 상태 관리 + 저장 로직
}
