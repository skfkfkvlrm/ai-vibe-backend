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
- 같은 DB를 쓰는 서비스(dog 계열, board 계열)를 동시에 기동하면 스키마가 충돌할 수 있습니다.
- 빌드: 각 폴더에서 `.\gradlew.bat build`
- 프론트엔드는 `ai-vibe-frontend` 저장소를 참고하세요.