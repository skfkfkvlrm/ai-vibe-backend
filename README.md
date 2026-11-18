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