# 루틴 체크 어플 — 개발 기반 (Foundation)

이 저장소는 노션 기획 문서의 "개발 순서 및 역할 분담" 1단계(기반 세팅)를 코드로 만든 것입니다.
**프로젝트 구조 / Room DB 스키마 / 온보딩(권한 요청 플로우)** 3가지가 실제로 구현되어 있고,
나머지 화면·기능은 각자 담당 패키지에 `TODO(이름)` 주석으로 시작점만 잡아뒀습니다.

## 지금 바로 실행되는 것

- 앱을 실행하면 `OnboardingActivity`가 뜨고, 3개 권한(오버레이/알림/배터리 최적화) 상태를 보여줍니다.
- 권한을 모두 허용하면 `MainActivity`(홈 자리, 아직 빈 화면)로 이동합니다.
- Room DB 4개 테이블(`routines`, `routine_apps`, `routine_checklist_state`, `app_notification_badges`)이
  정의되어 있고, `Hilt`로 어디서든 주입받아 쓸 수 있습니다.

## 팀원별 시작 위치

| 담당 | 이름 | 시작할 패키지 | 참고 |
|---|---|---|---|
| 온보딩 · 챗헤드 | 변공록 | `onboarding/` (완료됨, 리뷰만) → `chathead/` | `ChatHeadService.kt`, `ChatHeadView.kt`에 TODO 있음 |
| 루틴/앱 등록 | 이현규 | `ui/home/`, `ui/appregister/` | `RoutineRepository`, `AppRegistryRepository`는 이미 구현되어 있어 바로 화면만 붙이면 됩니다 |
| 알림/스케줄 | 석광현 | `scheduler/`, `notification/`, `ui/routineedit/` | `RoutineAlarmScheduler`, `AppNotificationListenerService`에 TODO 있음 |

각 TODO 주석에 "누가, 무엇을, 어떤 클래스를 참고해서" 할지 적어뒀으니 `TODO(이름)`으로 전체 검색하면
자기 작업 목록이 바로 나옵니다.

## 프로젝트 구조

```
app/src/main/java/com/routinecheck/app/
├── RoutineCheckApp.kt          # Application (Hilt 진입점)
├── MainActivity.kt             # 홈 진입점 (이현규가 HomeScreen 연결)
├── di/                         # Hilt 모듈 (DB, Repository)
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── entity/             # Room 테이블 4개
│   │   └── dao/                # 각 테이블 DAO
│   └── repository/             # RoutineRepository, AppRegistryRepository, NotificationBadgeRepository
├── onboarding/                 # ✅ 구현 완료 (변공록 작업분)
├── chathead/                   # ⬜ TODO (변공록)
├── scheduler/                  # ⬜ TODO (석광현)
├── notification/               # ⬜ TODO (석광현)
└── ui/
    ├── home/                   # ⬜ TODO (이현규)
    ├── appregister/            # ⬜ TODO (이현규)
    └── routineedit/            # ⬜ TODO (석광현/이현규)
```

## 시작하는 법

1. Android Studio (Koala 이상 권장)로 이 폴더를 엽니다.
2. Gradle Sync가 끝나면 `app` 모듈을 실행 (에뮬레이터 또는 실기기, minSdk 26 이상).
3. 온보딩 화면에서 권한 3개를 허용해보고 잘 넘어가는지 확인합니다.
4. 자기 담당 패키지의 `TODO`를 하나씩 지워가며 작업 → `feature/[기능명]` 브랜치에서 작업 후 `develop`으로 PR
   (브랜치 전략은 노션 문서 "🗂️ 브랜치 전략 및 커밋 규칙" 참고).

## DB 스키마를 바꾸게 되면

1. `data/local/entity/`의 Entity 클래스 수정
2. `data/local/AppDatabase.kt`의 `version` 숫자 +1
3. `di/DatabaseModule.kt`의 `Room.databaseBuilder(...)`에 `Migration` 객체 추가
4. 팀 채팅/노션 "🧾 회의 기록"에 스키마 변경 사실 공유 (다른 사람 로컬 DB가 깨질 수 있어서)

## 아직 정해지지 않은 것 (팀 회의에서 결정 필요)

- `minSdk` / `targetSdk` 최종 확정 (`app/build.gradle.kts`에 TODO 표시해둠)
- 알림 접근 권한(`NotificationListenerService`)은 온보딩 3개 권한과 별개 플로우라 UX를 어떻게 안내할지
- 챗헤드 확장 팝업의 실제 디자인/애니메이션 디테일
