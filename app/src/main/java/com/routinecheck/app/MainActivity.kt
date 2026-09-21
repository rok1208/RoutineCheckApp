package com.routinecheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

/**
 * 온보딩(권한 요청) 완료 후 도착하는 홈 진입점.
 *
 * TODO(이현규): ② 홈 (루틴 목록) 화면 구현
 *   - ui/home/HomeScreen.kt, ui/home/HomeViewModel.kt 를 만들어 이 자리에 연결해주세요.
 *   - RoutineRepository.getRoutines() 를 Flow로 collect 해서 루틴 카드 리스트를 그리면 됩니다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    // TODO: HomeScreen() 으로 교체
                    Text("홈 화면 자리 - HomeScreen()을 여기에 연결하세요")
                }
            }
        }
    }
}
