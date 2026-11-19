# ai-vibe-backend

Spring Boot 백엔드 6개를 폴더별로 묶은 저장소입니다. 각 폴더는 독립 Gradle 프로젝트이며 각자 빌드합니다. 원본 저장소의 커밋 히스토리를 보존했습니다.

| 폴더 | 원본 저장소 | 포트 | DB |
|---|---|---|---|
| demo-boot | demo-boot | 8081 | dbdemoboot |
| demo-dog | demo-dog | 8082 | dbdemodog |
| demo-dog2 | demo-dog2 | 8083 | dbdemodog (demo-dog과 공유) |
| demo-board | demo-board | 8084 | dbdemoboard |
| demo-board2 | demo-board2 | 8085 | dbdemoboard (공유) |
| pc-mall | demo-mall | 8086 | dbdemoboard (공유) |

- 포트는 환경변수 `SERVER_PORT`로 바꿀 수 있습니다.
- 업로드 이미지 폴더는 환경변수 `UPLOAD_DIR`로 정합니다(현재 `demo-dog`가 사용). 이 PC에서는 사용자 환경변수로 `D:\skfkfkvlrm\ai-vibe-data\uploads`를 등록했고, 값이 없으면 실행 폴더 기준 `uploads`를 씁니다. 환경변수는 새로 여는 터미널·IDE부터 적용되니, 등록 직후에는 터미널과 VS Code를 다시 연 뒤 서버를 기동하세요. 업로드 기능을 가진 백엔드를 새로 만들 때도 같은 변수를 읽으면 같은 폴더를 공유합니다.
- 같은 DB를 쓰는 서비스(dog 계열, board 계열)를 동시에 기동하면 스키마가 충돌할 수 있습니다.
- 빌드: 각 폴더에서 `.\gradlew.bat build`
- 프론트엔드는 `ai-vibe-frontend` 저장소를 참고하세요.

## demo-board 실행 안내

프로파일은 두 가지입니다. 지정하지 않으면 `local`(개발)로 실행됩니다.

| 프로파일 | 켜는 법 | DB 접속 정보 | 특징 |
|---|---|---|---|
| `local`(기본) | 지정 없음 | `demo-board/config/application-local.properties`(Git에 올리지 않음) | 스키마를 자동으로 갱신(`ddl-auto=update`) |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 스키마는 검증만(`validate`), 오류 응답에 내부 정보 없음, 액추에이터는 관리 포트에서만 |

- **새 PC에서 처음 실행**: `demo-board/config/application-local.properties.example`을 같은 폴더의 `application-local.properties`로 복사해 값을 채웁니다. 작업 폴더가 `demo-board`일 때 자동으로 읽히며, 다른 위치에서 실행하면 위 환경변수 3개를 대신 씁니다.
- **`JWT_SECRET`**(32바이트 이상)은 어느 프로파일이든 환경변수로 반드시 지정합니다.
- **실행**: `demo-board` 폴더에서 `.\gradlew.bat bootRun` 또는 `java -jar build\libs\demo-board-0.0.1-SNAPSHOT.jar`
- **헬스 체크**: `local`은 `http://localhost:8084/actuator/health`. `prod`는 서비스 포트의 `/livez`·`/readyz`(상태만 반환)와, 이 PC에서만 열리는 관리 포트(`MANAGEMENT_PORT`, 기본 9090)의 `/actuator/health/core`(DB만)·`/actuator/health/ops`(구성요소 상세)입니다.
- **제한 기록 자동 정리**: 로그인 실패(`login_attempt`)와 비회원 쓰기 제한(`write_rate_limit`) 기록 중 1일 지난 행을 1시간마다 지웁니다. 관리자 감사 로그(`admin_audit_log`)는 지우지 않습니다.
- **프론트엔드와 함께 실행**: 백엔드를 8084에서 띄운 뒤 `ai-vibe-frontend/board-project`에서 `npm run dev`(개발 서버가 `/api` 요청을 8084로 전달합니다).