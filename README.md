# 루틴 체크 어플

안드로이드의 앱 접근 방식이 비효율적이라는 문제의식에서 시작한 앱입니다.
**화면 위 챗헤드 하나로 주요 앱을 바로 열고 확인**하는 것이 핵심이고,
루틴 · 알림 · 메모 · 통계는 챗헤드를 더 잘 쓰게 해주는 보조 기능입니다.

## 기능 요약

| 기능 | 내용 |
|---|---|
| 챗헤드 (핵심) | 원형 기본 → 화면 끝까지 밀면 가장자리 바 → 바를 안쪽으로 밀면 다시 원 / 탭하면 펼침 / 아래 X로 드래그하면 끄기 |
| 펼침 방식 | **휠 캐러셀(기본)**: 위아래로 돌려 고르고 가운데 카드를 탭 · **컨테이너(설정)**: 사각형 목록 |
| 표시 순서 | ① 진행 중 루틴의 확인 전 앱 → ② 확인한 앱 → ③ 챗헤드 전용 앱 (중복은 한 번만) |
| 자동 체크 | 챗헤드에서 루틴 앱을 열면 오늘 체크 완료, 길게 누르면 수동 체크/해제 |
| 알림 배지 | 알림 접근 권한으로 실제 미확인 알림 수 표시 (전화·알람 등은 "!") |
| 루틴 | 요일·시각·앱 등록, 정각 알람(AlarmManager), 스누즈, 챗헤드 자동 펼침, 시계 앱 알람 연동 |
| 루틴 완료 시 | 설정에 따라 유지 / 가장자리 바로 축소 / 숨김 |
| 홈 | 챗헤드 카드, 이번 주 달력(메모), 오늘의 루틴 체크리스트 |
| 통계 | 오늘 · 30일 완료율, 연속 달성일, 최근 7일 막대, 루틴별 완료율 |

## 프로젝트 구조

```
app/src/main/java/com/routinecheck/app/
├── RoutineCheckApp.kt / MainActivity.kt
├── chathead/        ⭐ ChatHeadService(오버레이 창 관리·터치), ChatHeadOverlayUi(원·바·캐러셀·컨테이너), ChatHeadController
├── domain/          순수 로직: RoutineSchedule(다음 알람 계산), ChatHeadOrdering(챗헤드 표시 순서)
├── data/local/      Room DB v4 (entity/, dao/, Migrations.kt)
├── data/repository/ Routine, Today, Stats, ChatHeadSettings, ChatHeadApp, Memo, NotificationBadge, AppRegistry
├── scheduler/       RoutineAlarmScheduler, RoutineAlarmReceiver, BootCompletedReceiver
├── notification/    AppNotificationListenerService(배지), NotificationChannels
├── onboarding/      필수(오버레이·알림·정확 알람) / 권장(알림 접근·배터리) 권한
├── ui/              home, routines, routineedit, apppicker, chatheadapps, settings, stats, memo, navigation, theme, common
└── util/            DayMask, DateTimeUtils, PermissionUtils, SystemAlarmLinker, ChatHeadIconStore
```

## DB 버전 이력

| 버전 | 변경 |
|---|---|
| v1 | routines, routine_apps, routine_checklist_state, app_notification_badges |
| v2 | + chathead_settings |
| v3 | + daily_memos |
| v4 | + chathead_apps, chathead_settings.uiStyle / enabled |

스키마를 바꿀 때: Entity 수정 → `AppDatabase.version` +1 → `Migrations.kt`에 Migration 추가 → `ALL_MIGRATIONS`에 등록.

## 실행

1. Android Studio로 폴더를 열고 Gradle Sync
2. `app` 실행 (minSdk 26 / targetSdk 35)
3. 온보딩에서 필수 권한 3개 허용 → 홈에서 챗헤드 켜기
4. 단위 테스트: `app/src/test/.../RoutineLogicTest.kt` 우클릭 → Run
