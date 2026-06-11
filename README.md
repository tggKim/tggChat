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

### 타 유저 조회(GET /user/{userId})
1. 요청 경로의 userId로 조회 대상 유저 식별
2. 조회 대상 유저 존재와 삭제여부 검증
3. 조회 대상 유저 정보 응답

### 본인 조회(GET /me)
1. JWT 인증 정보에서 로그인한 유저 ID 추출
2. 로그인 유저 존재와 삭제여부 검증
3. 본인 유저 정보 응답 

### 유저 정보 수정(PATCH /me)
1. JWT 인증 정보에서 로그인한 유저 ID 추출
2. 요청값 검증
3. 로그인 유저 존재와 삭제여부 검증
4. 수정할 유저명이 기존 유저명과 다른 경우 유저명 중복 검사
5. 유저명 수정

### 유저 삭제
1. JWT 인증 정보에서 로그인한 유저 ID 추출
2. 로그인 유저 존재와 삭제여부 검증
3. 유저 삭제 상태로 변경

### 유저 관리 주의사항
- 유저 삭제는 DB row를 제거하는 것이 아닌 deleted 값을 true로 변경하는 소프트 삭제 방식
- 이메일과 유저명은 DB unique 제약이 걸려있어 삭제된 유저의 이메일/유저명도 재사용할 수 없다.

</details>

<details>
 
<summary>친구 관리</summary>

### 친구 추가(POST /friends)
1. JWT 인증 정보에서 로그인한 유저 ID 추출
2. 요청값 검증
3. 로그인 유저 존재와 삭제여부 검증
4. 요청한 username으로 친구 추가 대상 유저 조회
5. 친구 추가 대상 유저 존재와 삭제여부 검증
6. 자기 자신을 친구로 추가하는 요청인지 검증
7. 이미 친구로 등록된 유저인지 검증
8. 친구 관계 저장

### 친구 목록 조회(GET /friends)
1. JWT 인증 정보에서 로그인한 유저 ID 추출
2. 로그인 유저 존재와 삭제여부 검증
3. 로그인 유저 ID를 기준으로 삭제되지 않은 친구 목록 조회
5. 친구 ID와 친구 유저명 응답

### 친구 관리 주의사항
- 친구 관계는 단방향 관계로 유저 A가 유저 B를 친구로 추가해도 유저 B의 친구 목록에 유저 A가 자동으로 추가되지는 않는다.
- 삭제된 유저는 친구로 추가할 수 없고, 친구 목록 조회에서도 제외된다.

</details>

<details>
 
<summary>인증/인가</summary>

### 토큰 관리 전략

- AccessToken과 RefreshToken을 사용한다.

- AccessToken은 요청 인증에 사용하고, RefreshToken은 AccessToken 재발급에 사용한다.

  - AccessToken은 응답 바디로 전달하고, 클라이언트는 Authorization 헤더에 Bearer 형식으로 담아 요청한다.

  - RefreshToken은 HttpOnly 쿠키로 전달한다.

- 세션 식별자인 sid를 사용하여 여러 로그인 세션을 구분한다.

- AccessToken은 Redis에 저장하지 않고 JWT 자체의 서명, 만료 시간, 토큰 타입을 기준으로 검증한다.

- RefreshToken만 Redis에 저장하여 재발급 가능 여부를 서버에서 제어한다.

### 레디스 Key 구조

- RT:{sid} = RefreshToken

### 1. 로그인(POST /login)
1. 요청값 검증
2. 이메일로 사용자 조회 후 존재/탈퇴 여부 검증
3. 비밀번호 일치 검증
4. 기존 RefreshToken 쿠키가 존재하면 파싱을 시도하고 정상적인 RefreshToken이면 해당 sid의 Redis RefreshToken 삭제
5. 새로운 sid 생성
6. AccessToken/RefreshToken 발급
7. Redis에 RT:{sid} 형식으로 RefreshToken 저장
8. AccessToken은 응답 바디, RefreshToken은 HttpOnly 쿠키로 전달

### 2. 로그아웃(POST /logout)
1. JWT 인증 정보에서 현재 세션의 sid 추출
2. sid를 이용해 Redis에서 RT:{sid} 삭제
3. RefreshToken 쿠키 만료 처리
4. 클라이언트는 보관 중인 AccessToken 폐기

### 3. 토큰 재발급(POST /refresh)
1. 쿠키에서 RefreshToken 획득
2. RefreshToken 자체의 서명, 만료 시간, 토큰 타입 검증
3. 토큰 타입이 refresh인지 검증
4. sid를 추출하여 Redis의 RT:{sid} 값과 현재 RefreshToken 일치 여부 검증
5. sub로 사용자 조회 후 존재/탈퇴 여부 검증
6. 같은 sid로 새로운 AccessToken, RefreshToken 생성
7. Redis의 RT:{sid} 값을 새로운 RefreshToken으로 갱신
8. AccessToken은 응답 바디, RefreshToken은 HttpOnly 쿠키로 전달

### 스프링 시큐리티 흐름

1. 공개 요청용 시큐리티 체인과 인증 요청용 시큐리티 체인을 각각 빈으로 등록한다.

   1-1. 화이트리스트에 해당하는 요청은 공개 요청용 시큐리티 체인에서 처리된다.
  이때 먼저 매칭된 체인 하나만 적용된다.

2. 화이트리스트에 해당하지 않는 요청은 인증 요청용 시큐리티 체인에서 처리된다.

   2-1. 인증 요청용 체인에 등록된 `JwtSecurityFilter`가 `Authorization` 헤더에서 AccessToken을 추출하고 검증한다.

   2-2. 토큰 검증 과정에서 발생한 `ErrorException`은 `JwtSecurityFilter`에서 에러 응답을 생성한다.

   2-3. 토큰 검증이 성공하면 `AuthenticatedUser`를 기반으로
  `UsernamePasswordAuthenticationToken`을 생성하고, 이를 `SecurityContextHolder`에 저장한다.

   2-4. 이후 컨트롤러에서는 `@AuthenticationPrincipal`을 통해 인증 사용자 정보를 꺼내 사용하고,
  서비스는 전달받은 `userId`를 기반으로 비즈니스 로직을 수행한다.

</details>

<details>
 
<summary>채팅방</summary>

</details>

<details>
 
<summary>메시징</summary>

</details>
