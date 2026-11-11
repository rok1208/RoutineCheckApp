# 루틴 체크 R8 규칙
# Compose · Room · Hilt · Navigation 은 라이브러리에 포함된 consumer 규칙으로 처리됨.
# 이 앱은 리플렉션/JSON 직렬화를 쓰지 않아 추가 keep 규칙이 필요 없음.

# 플레이 콘솔 비정상 종료 스택트레이스를 읽을 수 있게 줄 번호 유지 (mapping.txt 업로드와 함께 사용)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# v0.7.1: 릴리스 빌드에서 디버그·정보 로그 제거 (루틴 ID·알람 시각 등이 기기 로그에 남지 않도록)
# 경고(Log.w)·오류(Log.e)는 문제 추적용으로 남김
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
