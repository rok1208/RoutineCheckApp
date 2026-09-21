package com.routinecheck.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application 진입점. Hilt DI 그래프가 여기서부터 생성됩니다.
 * 새 모듈을 추가해도 이 파일은 건드릴 필요 없습니다 (di/ 패키지에 모듈만 추가하면 됩니다).
 */
@HiltAndroidApp
class RoutineCheckApp : Application()
