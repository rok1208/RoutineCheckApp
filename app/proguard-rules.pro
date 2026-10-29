# 루틴 체크 R8 규칙
# Compose · Room · Hilt · Navigation 은 라이브러리에 포함된 consumer 규칙으로 처리됨.
# 이 앱은 리플렉션/JSON 직렬화를 쓰지 않아 추가 keep 규칙이 필요 없음.

# 플레이 콘솔 비정상 종료 스택트레이스를 읽을 수 있게 줄 번호 유지 (mapping.txt 업로드와 함께 사용)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
