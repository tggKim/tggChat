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

### 로그인(POST /login)
1. 요청값 검증
2. 이메일로 사용자 조회 후 존재/탈퇴 여부 확인
3. 비밀번호 일치 검증
4. AccessToken/RefreshToken 발급 후 Redis에 저장
5. AccessToken은 응답 바디, RefreshToken은 HttpOnly 쿠키로 전달

</details>

<details>
 
<summary>채팅방</summary>

</details>

<details>
 
<summary>메시징</summary>

</details>
