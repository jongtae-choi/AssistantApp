# 개인비서 앱 (Android, Kotlin/Compose)

`server.js`(Claude API 기반 개인비서 서버)를 붙여서, 사진/PDF를 찍거나 골라서 분석하고,
엑셀/PPT 문서로 정리해서 메일로 받아보는 안드로이드 네이티브 앱입니다.
기존에 HTTP Shortcuts(혹은 Tasker)로 하던 흐름을, 앱 UI + 안드로이드 공유 기능으로 대체합니다.

## v1에서 되는 것 / 안 되는 것

- 됨: 사진 선택/카메라 촬영/PDF 선택 → 지시사항 입력 → `/analyze-image`로 분석 결과 텍스트 확인
- 됨: 분석한 사진을 문서(엑셀/PPT)로 정리해서 지정한 이메일로 발송(`/pipeline/photo-to-gmail`)
- 됨: 다른 앱(갤러리, 카카오톡 등)에서 사진을 **"공유하기" → 개인비서**로 바로 넘기면 자동으로 선택된 상태로 앱이 열림
- 아직 안 됨(다음 버전 예정): Gmail 첨부파일 검색/다운로드, 리서치 단독 실행(`/research`), 생성된 문서를 앱 안에서 이미지로 바로 미리보기(`/make-image`), 캘린더/연락처 관련 기능

## 빌드 방법

1. 이 폴더를 Android Studio에서 "Open"으로 엽니다.
2. `gradlew`가 없다는 안내가 뜨면 터미널에서 한 번만 실행:
   ```
   gradle wrapper --gradle-version 8.7
   ```
3. Gradle Sync 후 실행 버튼으로 설치하거나, `Build > Generate Signed Bundle / APK`로 APK를 뽑아 사이드로드하세요.

## 앱에서 최초 설정

처음 실행하면 "서버 연결 설정" 화면이 뜹니다.

- **서버 주소**: `server.js`가 실행 중인 주소 (예: `https://yyyy.ngrok-free.app`). `server.js`의 라우트가 전부 `/assistant/api/...`로 시작하므로, 앱은 자동으로 그 프리픽스를 붙여서 호출합니다.
- **인증 토큰**: `.env`의 `MY_SECRET_TOKEN`과 동일한 값
- **기본 수신 이메일**: "문서 생성 + 메일 발송"에서 받는사람 칸에 기본으로 채워질 이메일 (선택)

## 공유(Share) 연동

안드로이드에서 사진을 길게 눌러 "공유" → 목록에서 "개인비서" 선택하면, 이 앱이 그 사진을 자동으로 불러온 상태로 열립니다.
PDF도 동일하게 동작합니다(다른 앱이 PDF를 공유 대상으로 등록해뒀다면).

## 알아두면 좋은 점

- `/analyze-image`, `/pipeline/photo-to-gmail` 모두 서버가 Claude API를 호출하기 때문에 응답까지 수 초~수십 초 걸릴 수 있습니다. 앱의 네트워크 타임아웃은 90초로 넉넉히 잡아뒀습니다.
- `pipeline/photo-to-gmail`은 서버가 먼저 "접수했다"는 응답을 즉시 주고, 실제 결과(문서)는 나중에 이메일로 도착하는 구조입니다(서버 쪽 설계 그대로 유지).
- Gmail 발송 기능을 쓰려면 서버(`server.js`) 쪽에 `GMAIL_REFRESH_TOKEN`이 이미 설정되어 있어야 합니다(앱과는 무관, 서버 설정 문제).
