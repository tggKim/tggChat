# FAIL.md

## 목적
- 반복되는 실패 원인과 해결 방법을 짧게 기록한다.
- 실패가 다시 발생하면 이 문서를 먼저 확인하고, 기존 원인과 일치하는지부터 검증한다.
- 새로운 원인이 확인되면 재현 조건, 원인, 해결 방안을 같은 형식으로 추가한다.

## 운영 규칙
- 실패 조사 전 이 문서를 먼저 읽는다.
- 기존 기록과 같은 원인이면 먼저 기록된 해결 절차를 적용한다.
- 같은 실패가 2회 이상 반복되면 코드만 다시 고치지 말고 테스트, 스크립트, 환경 설정 같은 검증 장치를 보강할지 검토한다.
- 새로 확인한 원인은 날짜와 함께 아래 기록 섹션에 추가한다.

## 기록

### 2026-04-21 - `gradlew.bat build` 실패

#### 1. Gradle Wrapper 샌드박스 실패
- 증상: `Could not create parent directory for lock file C:\Users\CodexSandboxOffline\.gradle\wrapper\dists\...`
- 원인: 샌드박스 환경에서 Gradle Wrapper 캐시 디렉터리를 만들 수 없었다.
- 해결: 빌드를 샌드박스 밖에서 다시 실행하거나, 쓰기 가능한 `GRADLE_USER_HOME`을 지정한 뒤 실행한다.

#### 2. `SECRET` 환경 변수 누락으로 테스트 실패
- 증상: `ChatApplicationTests.contextLoads()` 실패, `Could not resolve placeholder 'SECRET' in value "${SECRET}" <-- "${JWT_SECRET_KEY}"`
- 원인: `src/main/resources/application.yml`의 `JWT_SECRET_KEY: ${SECRET}` 설정 때문에 테스트 시 `SECRET` 환경 변수가 필요하지만, 빌드 환경에 값이 없었다.
- 해결: 빌드/테스트 전에 32바이트 이상 `SECRET` 환경 변수를 설정한다.
- 확인된 실행 예시:

```powershell
$env:SPRING_PROFILES_ACTIVE='local'
$env:SECRET='0123456789abcdefghijklmnopqrstuvwxyz'
.\gradlew.bat build
```

### 2026-04-22 - `gradlew.bat clean build` 테스트 실패

#### 1. Redis 미실행으로 인한 테스트 실패
- 증상: `ChatApplicationTests.contextLoads()`, `ChatServiceLockTest` 실패, `Failed to start bean 'redisMessageListenerContainer'`, `Unable to connect to Redis`
- 원인: 테스트 실행 시 `local` 프로필 기준 Redis(`localhost:6379`)에 연결하지 못했다.
- 해결: 로컬 Redis가 실행 중인지 먼저 확인한 뒤 다시 빌드/테스트를 실행한다.
