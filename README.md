# tggChat 채팅 서버
WebSocket(STOMP) 기반의 실시간 채팅 서버로, Redis를 활용해 분산 환경에서도 안정적인 메시징을 지원하는 프로젝트입니다.

# 기술 스택
- **언어** : Java 17
- **프레임워크** : Spring Boot 3.5
- **실시간 통신** : WebSocket(STOMP)
- **데이터베이스** : MySQL 8, Spring Data JPA, MyBatis
- **메시징/캐싱** : Redis Pub/Sub
- **인증/인가** : JWT, Spring Security
- **클라우드 및 CI/CD** : Docker
- **빌드 도구** : Gradle

# ERD
<img width="2531" height="1253" alt="chatDB (1)" src="https://github.com/user-attachments/assets/ce545f8a-1e7f-4432-ae29-1399e0692afd" />

# 주요 기능

# 기능별 흐름
<details>
 
<summary>유저 관리</summary>

### 유저 생성(POST /user)
1. 요청값 검증
2. 이메일 중복 검사
3. 유저명 중복 검사
4. 비밀번호 인코딩
5. 유저 저장
6. 저장된 유저 정보 응답

### 타 유저 조회

### 본인 조회

### 유저 정보 수정

### 유저 삭제

</details>

<details>
 
<summary>친구 관리</summary>

</details>

<details>
 
<summary>인증/인가</summary>

### 토큰 관리 전략

- 유저당 하나의 AccessToken/RefreshToken 세트만을 유효하게 유지하는 방식

- 즉 유저는 단 하나의 세션만을 로그인상태로 유지할 수 있다

### 레디스 Key 구조

- RT:{userId} = refreshToken
- AT:{userId} = accessToken

### 1. 로그인(POST /login)
1. 요청값 검증
2. 이메일로 사용자 조회 후 존재/탈퇴 여부 검증
3. 비밀번호 일치 검증
4. AccessToken/RefreshToken 발급 후 Redis에 저장(기존 유저의 토큰이 덮어씌워진다)
5. AccessToken은 응답 바디, RefreshToken은 HttpOnly 쿠키로 전달

### 2. 로그인 여부 확인(POST /login-status)
1. 요청값 검증
2. 이메일로 사용자 조회 후 존재/탈퇴 여부 검증
3. redis에서 사용자 RefreshToken의 존재여부를 응답 바디로 리턴
(프론트에서 이를 활용하여 유저의 다른 세션에서의 로그인 여부 파악)

### 3. 로그아웃(POST /logout)
1. SecurityContext 에서 userId 획득
2. userId를 이용해 redis에서 사용자의 AccessToken, RefreshToken 모두 삭제

### 4. 토큰 재발급(POST /refresh)
1. 쿠키에서 RefreshToken 획득
2. redis에 동일한 RefreshToken이 저장되어 있는지 검증
3. 검증 후 새로운 AccessToken, RefreshToken 생성
4. AccessToken은 응답 바디, RefreshToken은 HttpOnly 쿠키로 전달

</details>

<details>
 
<summary>채팅방</summary>

</details>

<details>
 
<summary>메시징</summary>

</details>
