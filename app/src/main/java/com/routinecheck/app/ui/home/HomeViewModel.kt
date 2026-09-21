package com.routinecheck.app.ui.home

import androidx.lifecycle.ViewModel
import com.routinecheck.app.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val routineRepository: RoutineRepository
) : ViewModel() {
    val routines = routineRepository.getRoutines()
    // TODO(이현규): StateFlow로 변환해서 UI에 노출
}
