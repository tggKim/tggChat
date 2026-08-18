# AUTHFLOW.md

## 1. 문서 목적과 기준

이 문서는 현재 서버 코드가 인증과 세션을 처리하는 실제 흐름을 설명한다. 로그인 화면이나 UI 상태를 설명하는 문서가 아니라, 다음 서버 동작을 코드 기준으로 추적하기 위한 문서다.

- Spring Security가 공개 요청과 보호 요청을 어떻게 나누는지
- AccessToken, RefreshToken, MediaToken을 언제 만들고 어디에서 검증하는지
- `sid`와 Redis를 이용한 로그인 세션 관리 방식
- 로그인, 재발급, 로그아웃, 회원 삭제가 토큰 상태에 주는 영향
- REST, 메시지 파일 조회, WebSocket/STOMP가 각각 어떤 인증 경로를 사용하는지
- 서버가 보장하는 범위와 프론트엔드가 별도로 관리해야 하는 범위

문서와 주석보다 실행 코드를 우선한다. 현재 구현은 **유저당 하나의 세션만 허용하는 단일 세션 정책이 아니다.** `sid`별 RefreshToken 세션을 최대 10개까지 유지하는 다중 세션 정책이다. 같은 브라우저가 가진 기존 RefreshToken은 새 로그인 때 가능한 범위에서 정리하지만, 다른 브라우저와 기기의 세션은 함께 유지된다.

현재 인증의 핵심은 다음과 같다.

1. 보호 REST API는 AccessToken을 검증하는 stateless 방식이다.
2. RefreshToken만 Redis에 저장하며, 재발급 때 JWT와 Redis의 현재 값을 모두 확인한다.
3. MediaToken은 메시지 파일을 브라우저에서 조회하기 위한 별도 stateless JWT다.
4. WebSocket HTTP 핸드셰이크는 공개하고, STOMP `CONNECT` 프레임의 AccessToken으로 사용자를 확정한다.
5. JWT 검증만으로 활성 사용자나 도메인 권한을 보장하지 않는다. 삭제 여부, 친구 관계, 채팅방 참여 상태 등은 서비스/조회 쿼리에서 다시 검증한다.

---

## 2. 인증 구성요소와 책임

| 구성요소 | 현재 책임 |
|---|---|
| `SecurityConfig` | 공개/보호 요청을 두 개의 `SecurityFilterChain`으로 분리하고 CORS, CSRF, 세션 정책을 설정한다. |
| `SecurityWhitelist` | HTTP Method와 Ant 경로 패턴 조합으로 공개 요청을 정의한다. |
| `JwtSecurityFilter` | 보호 REST 요청의 `Authorization` 헤더를 검증하고 Spring `SecurityContext`를 구성한다. |
| `AccessTokenAuthenticator` | Bearer 형식, JWT 파싱, `type=access`를 확인하고 `AuthenticatedUser`를 만든다. REST와 STOMP가 이 로직을 공유한다. |
| `JwtUtils` | 세 종류의 JWT 생성, 서명/만료/형식 파싱, 토큰 타입과 `sid` 조회를 담당한다. |
| `AuthenticatedUser` | 보호 REST 요청에서 사용하는 principal이다. `userId`, `sid`만 가진다. |
| `AuthController` | 로그인/재발급 응답의 AccessToken body와 RefreshToken/MediaToken 쿠키를 구성하고, 로그아웃 때 쿠키를 만료시킨다. |
| `AuthService` | 계정 검증, 토큰 발급, RefreshToken 재생성·Redis 교체, 현재 세션 로그아웃을 조합한다. |
| `RedisTokenStore` | `sid`별 RefreshToken, 유저별 `sid` 인덱스와 최대 세션 수를 관리한다. |
| `JwtChannelInterceptor` | STOMP `CONNECT` 프레임의 AccessToken을 검증하고 `StompPrincipal`을 설정한다. |
| `StompPrincipal` | WebSocket 세션 principal이다. 이름은 `userId` 문자열이며 `sid`를 보관하지 않는다. |
| `ChatRoomSubscriptionInterceptor` | 정확히 `/topic/chatRooms/{chatRoomId}`를 구독할 때 활성 사용자/활성 채팅방 멤버인지 확인한다. |
| `StoredFileService.findMessageFile` | 공개 HTTP 경로로 들어온 메시지 파일 요청에서 MediaToken, 활성 사용자, 채팅방 접근 범위를 직접 검증한다. |
| `UserService.deleteUser` | 회원을 논리 삭제하고 Redis 사용자 세션 인덱스에 등록된 RefreshToken 세션을 제거한다. |

`@SecurityRequirement(name = "JWT Auth")`는 Swagger/OpenAPI 표시용이다. 실제 보호 여부는 `SecurityConfig`와 `SecurityWhitelist`가 결정한다.

---

## 3. Spring Security 요청 경계

### 3.1 공통 보안 설정

두 필터 체인은 공통으로 다음 설정을 사용한다.

- CORS 활성화
- CSRF 비활성화
- `SessionCreationPolicy.STATELESS`
- Form Login 비활성화
- HTTP Basic 비활성화
- Spring Security 기본 Logout 비활성화

따라서 서버는 HTTP 세션이나 `JSESSIONID`를 로그인 상태의 근거로 사용하지 않는다. 애플리케이션의 `/logout`은 Spring Security 기본 logout filter가 아니라 `AuthController`가 직접 처리한다.

`JwtSecurityFilter`는 Spring Bean이지만 서블릿 컨테이너의 글로벌 필터 자동 등록은 `FilterRegistrationBean#setEnabled(false)`로 막혀 있다. 이 필터는 두 번째 보안 체인 안에서만 `UsernamePasswordAuthenticationFilter`보다 앞에 실행된다. 이 설정이 없으면 공개 요청까지 포함해 필터가 중복 실행될 수 있다.

### 3.2 공개 요청: `@Order(1)`

`SecurityWhitelist`의 Method와 패턴이 모두 일치하는 요청만 첫 번째 체인에 들어간다. 이 체인은 `permitAll`이며 `JwtSecurityFilter`를 추가하지 않는다.

| Method | 패턴 | 실제 용도 |
|---|---|---|
| `GET` | `/swagger-ui/**` | Swagger UI |
| `GET` | `/v3/api-docs/**` | OpenAPI 문서 |
| `GET` | `/user/**` | 다른 사용자 공개 조회. 현재 컨트롤러 경로는 `/user/{userId}`다. |
| `POST` | `/user` | 회원가입 |
| `POST` | `/login` | 로그인 |
| `POST` | `/refresh` | RefreshToken 쿠키 기반 재발급 |
| `GET` | `/ws/**` | WebSocket/SockJS HTTP 진입 경로 중 GET 요청 |
| `GET` | `/profile-images/**` | 공개 프로필 이미지/썸네일 |
| `GET` | `/media/**` | 메시지 파일 HTTP 경로. 필터는 공개지만 서비스에서 MediaToken과 채팅방 권한을 검증한다. |

공개 체인은 요청에 유효하지 않은 `Authorization` 헤더가 있더라도 그 헤더를 인증에 사용하지 않는다.

현재 `/ws/**` 공개 규칙은 `GET`만 지정한다. SockJS transport가 POST 같은 non-GET HTTP 요청을 사용하면 보호 체인으로 들어가 HTTP `Authorization: Bearer ...`를 요구한다. STOMP CONNECT native header는 그 HTTP 필터 처리 뒤에 해석되므로 native header만으로는 이 fallback 요청을 통과시킬 수 없다.

### 3.3 보호 요청: `@Order(2)`

위 공개 규칙에 들어가지 않은 모든 요청은 두 번째 체인에서 `authenticated()`를 요구한다. 현재 각 API를 따로 열거하는 방식이 아니라 **화이트리스트 외 전부 보호**하는 구조다.

보호 요청은 다음 순서로 처리된다.

1. `JwtSecurityFilter`가 `Authorization` 헤더를 읽는다.
2. `AccessTokenAuthenticator`가 헤더와 JWT를 검증한다.
3. 검증 성공 시 `AuthenticatedUser(userId, sid)`를 만든다.
4. principal은 `AuthenticatedUser`, credentials는 `null`, authorities는 빈 목록인 `UsernamePasswordAuthenticationToken`을 만든다.
5. 이 객체를 `SecurityContextHolder`에 저장한다.
6. 이후 컨트롤러는 `@AuthenticationPrincipal AuthenticatedUser`로 `userId`와 `sid`를 받는다.

현재 role/authority claim이나 메서드 단위 `@PreAuthorize`는 없다. Spring Security의 역할은 요청이 유효한 AccessToken을 가졌는지 확인하는 데 한정되고, 객체 단위 인가는 서비스 계층이 담당한다.

### 3.4 CORS

REST 보안 체인의 CORS 설정은 다음과 같다.

- 허용 Origin: `http://localhost:5173`, `https://jiangxy.github.io`
- 허용 Method: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`
- 허용 Header: 전체
- Credential 허용: `true`

WebSocket endpoint 등록 계층은 별도로 `setAllowedOriginPatterns("*")`를 사용한다. 그러나 `/ws/**` HTTP handshake·SockJS transport도 Spring Security CORS를 거치므로 실제 cross-origin 접근은 위 두 Origin 제한을 함께 통과해야 한다. 그 뒤 STOMP 세션 사용자는 다시 `CONNECT` 단계의 AccessToken 검증을 통과해야 한다.

---

## 4. JWT 공통 형식과 토큰별 역할

### 4.1 공통 서명과 Claims

세 토큰은 모두 `JwtUtils`가 같은 비밀키와 HS256으로 서명한다.

| 위치 | 값/의미 |
|---|---|
| Header `typ` | `JWT` |
| `sub` | 문자열로 저장한 `userId` |
| `sid` | 로그인 세션 식별자. 새 로그인 때 UUID로 생성한다. |
| `type` | `access`, `refresh`, `media` 중 하나 |
| `iat` | 발급 시각 |
| `exp` | 만료 시각 |

현재 JWT에는 role, authority, username, email, issuer, audience, `jti`가 없다. 서버가 인증 주체를 구성할 때 사용하는 값은 `sub`와 `sid`다.

`JwtUtils.parseClaims`는 다음을 공통 검증한다.

- 설정된 비밀키를 사용한 JWT 서명 검증. 서버가 발급하는 토큰의 알고리즘은 HS256이다.
- 만료 시각 검증
- JWT 형식 및 지원 여부
- null/빈 토큰 여부

토큰 종류는 파싱과 별도로 각 사용처에서 확인한다.

- REST/STOMP 인증: `type=access`
- 재발급: `type=refresh`
- 메시지 파일 조회: `type=media`

현재 서버가 직접 발급한 JWT는 숫자형 `sub`와 UUID `sid`를 항상 포함한다. 검증 코드는 이 스키마를 전제로 하며, `sub`의 숫자 변환이나 `sid`의 존재를 별도 비즈니스 오류로 정규화하지는 않는다.

### 4.2 토큰 비교표

| 토큰 | TTL | 전달/보관 위치 | Redis 저장 | 검증 시점 | 서버 측 즉시 폐기 가능 범위 |
|---|---:|---|---|---|---|
| AccessToken | 10분 | 로그인/재발급 JSON body의 `accessToken`; 이후 `Authorization: Bearer ...` | 저장하지 않음 | 보호 REST 요청마다, STOMP `CONNECT` 때 | 별도 blacklist가 없어 토큰 자체는 만료까지 유효 |
| RefreshToken | 7일 | `refreshToken` HttpOnly 쿠키 | `RT:{sid}`에 현재 토큰 원문 저장 | `POST /refresh`에서 JWT와 Redis 값 모두 확인 | 로그아웃, 세션 수 초과, 회원 삭제 때 Redis 값 삭제 가능 |
| MediaToken | 10분 | `mediaToken` HttpOnly 쿠키 | 저장하지 않음 | 메시지 파일 GET 요청마다 | blacklist가 없어 토큰 자체는 만료까지 유효. 단, 활성 사용자와 채팅방 권한은 매 요청 재검증 |

AccessToken과 MediaToken에도 `sid`가 들어가지만, 현재 코드에서 Redis 세션 존재 여부를 조회하는 토큰은 RefreshToken뿐이다.

### 4.3 비밀키 설정

`application.yml`은 다음 연결을 사용한다.

```yaml
JWT_SECRET_KEY: ${SECRET}
```

`JwtUtils`는 `JWT_SECRET_KEY` 값을 UTF-8 바이트로 바꾸어 HMAC 키를 만든다. HS256 키 생성 조건을 만족하도록 `SECRET`은 최소 32바이트 이상이어야 하며, 값이 없으면 애플리케이션 컨텍스트 생성이 실패한다.

- 보안 운영 원칙상 실제 값은 환경 변수나 secret manager 등 저장소 밖에서 주입해야 한다.
- 다만 현재 추적 중인 `docker-compose.yml`은 `SECRET` 값을 리터럴로 직접 선언한다. 즉 현재 Compose 실행은 외부 주입 상태가 아니며, 값 노출 시 토큰 위조 위험이 있으므로 외부 secret으로 옮기고 기존 값은 회전해야 한다. 이 문서에는 실제 값을 싣지 않는다.

---

## 5. 쿠키 정책

로그인과 재발급 성공 응답은 `Set-Cookie` 헤더를 두 번 추가한다.

| 속성 | `refreshToken` | `mediaToken` |
|---|---|---|
| `HttpOnly` | `true` | `true` |
| `Secure` | `false` | `false` |
| `SameSite` | `Lax` | `Lax` |
| `Path` | `/` | `/` |
| `Max-Age` | 7일, 604800초 | 10분, 600초 |

`Secure=false`이므로 현재 응답에는 Secure 속성이 붙지 않는다. 운영 HTTPS에서만 전송되도록 강제하는 설정이 아니다. Swagger annotation의 RefreshToken 예시는 `Max-Age=1209600`으로 표시되어 있지만, 실제 `JwtUtils` 상수와 쿠키 생성 코드는 7일인 `604800`초를 사용한다.

로그아웃 성공 응답은 동일한 이름, `HttpOnly`, `Secure`, `SameSite`, `Path`를 사용하고 `Max-Age=0`으로 두 쿠키를 만료시킨다.

쿠키는 HttpOnly이므로 프론트엔드 JavaScript가 값을 직접 읽거나 삭제할 수 없다. 브라우저가 요청에 포함하고 응답의 `Set-Cookie`를 반영하도록 해야 한다.

---

## 6. Redis 세션 모델

### 6.1 Key 구조

`RedisTemplate<String, String>`은 key와 value에 `StringRedisSerializer`를 사용한다.

| Key | Redis 자료구조 | Value/Member | TTL |
|---|---|---|---|
| `RT:{sid}` | String | 해당 세션에서 현재 유효한 RefreshToken 원문 | 저장/회전 시점부터 7일 |
| `USER_SESSIONS:{userId}` | Sorted Set | member=`sid`, score=`System.currentTimeMillis()` | 해당 유저의 어떤 세션이든 저장/회전할 때 7일로 다시 설정 |

`USER_SESSIONS:{userId}`는 유저에서 `sid`들을 찾기 위한 역방향 인덱스다. AccessToken과 MediaToken은 Redis에 저장하지 않는다.

### 6.2 저장과 회전

`saveRefreshToken(userId, sid, token, ttl)`은 다음 Redis 명령을 순서대로 수행한다.

1. `RT:{sid}`에 RefreshToken을 저장하고 개별 TTL을 설정한다.
2. `USER_SESSIONS:{userId}` Sorted Set에 `sid`를 추가하거나 갱신하고 현재 epoch millisecond를 score로 저장한다.
3. 유저 세션 인덱스 전체 TTL을 RefreshToken TTL로 다시 설정한다.
4. 세션 개수를 확인하고 10개를 초과하면 Sorted Set에서 score가 가장 낮은 오래된 `sid`들을 조회한다.
5. 조회한 `sid`들의 `RT:{sid}` key를 먼저 일괄 삭제한다.
6. 그 다음 `USER_SESSIONS:{userId}` Sorted Set에서 같은 `sid` member들을 제거한다.

재발급은 기존 `sid`를 그대로 사용하므로 Sorted Set member는 늘어나지 않고 score만 최근 시각으로 갱신된다.

### 6.3 최대 세션 수

- 유저별 서로 다른 `sid`는 최대 10개다.
- 11번째 세션이 저장되면 가장 오래된 `sid`부터 제거된다.
- 세션 수 초과로 제거되는 것은 해당 `sid`의 RefreshToken 재발급 권한이다.
- 그 세션에서 이미 발급된 AccessToken과 MediaToken은 Redis를 확인하지 않으므로 각자의 남은 TTL 동안 토큰 자체의 검증을 통과할 수 있다.
- 해당 세션으로 이미 연결된 WebSocket도 자동으로 끊지 않는다.
- 개별 `RT:{sid}`가 TTL로 먼저 만료되어도 대응 `sid` member를 ZSET에서 즉시 제거하는 작업은 없다. 다른 세션 저장·회전이 `USER_SESSIONS:{userId}` 전체 TTL을 다시 7일로 연장하면 이런 stale sid가 인덱스에 남아 `ZCARD`의 10개 계산에 포함될 수 있다.
- 이후 10개 초과 정리가 실행되면 score가 오래된 stale sid가 제거 대상이 될 수 있지만, 초과하지 않는 동안에는 실제 재발급 가능한 세션 수보다 인덱스 count가 크게 보일 수 있다.

이 정책은 엄격한 단일 세션 정책이 아니다. 같은 유저가 여러 브라우저/기기에서 동시에 로그인할 수 있다.

### 6.4 삭제

현재 세션 삭제는 다음 두 작업이다.

1. `RT:{sid}` 삭제
2. `USER_SESSIONS:{userId}`에서 해당 `sid` 제거

전체 세션 삭제 메서드는 Sorted Set에 **등록된** 전체 `sid`를 읽고, 대응하는 `RT:{sid}`를 일괄 삭제한 다음 `USER_SESSIONS:{userId}` 자체를 삭제한다. 인덱스에 기록되지 않은 고아 `RT:{sid}`가 있다면 이 방식으로 찾을 수 없고 개별 TTL까지 남을 수 있다.

### 6.5 원자성 범위

Redis 저장, 비교, 회전, 개수 제한은 Redis transaction이나 Lua script 하나로 묶여 있지 않다.

- RefreshToken 검증의 `GET`과 새 토큰 `SET` 사이가 원자적이지 않다.
- 같은 RefreshToken으로 재발급 요청이 거의 동시에 들어오면 둘 다 기존 값 비교를 통과할 가능성이 있다. 생성 결과가 서로 다르면 마지막으로 저장된 원문만 이후 Redis 일치 검증을 통과한다.
- 토큰에는 `jti`나 별도 nonce가 없다. JWT NumericDate는 초 단위로 직렬화되므로 같은 `userId`, `sid`, `type`에 대해 같은 초에 다시 만든 RefreshToken은 이전 토큰과 byte 단위로 같을 수 있다. 이 경우 Redis 교체를 수행해도 원문이 달라지지 않아 엄격한 1회용 rotation을 보장하지 않는다.
- `RT` 저장, Sorted Set 갱신, TTL 갱신, 오래된 세션 정리는 각각 별도 명령이므로 중간 실패 시 일부 상태만 반영될 수 있다.
- 모든 세션 삭제도 Sorted Set 조회, token key 삭제, index 삭제가 하나의 원자 연산은 아니다.

따라서 프론트엔드는 재발급 요청을 동시에 여러 개 실행하지 않고 하나의 진행 중인 요청을 공유하는 방식으로 직렬화하는 것이 중요하다.

---

## 7. 로그인 흐름

### 7.1 인터페이스

- Method/Path: `POST /login`
- 보안 체인: 공개
- Request body:

```json
{
  "email": "user@example.com",
  "password": "password"
}
```

- 성공 body:

```json
{
  "accessToken": "..."
}
```

- 성공 부가 응답: `refreshToken`, `mediaToken` Set-Cookie

입력 검증은 다음과 같다.

- `email`: 필수, 이메일 형식, 최대 254자
- `password`: 필수

검증 실패 시 첫 번째 field error 메시지를 담은 `C001`, HTTP 400 응답을 반환한다.

### 7.2 서버 처리 순서

```text
AuthController
  -> AuthService.login
     -> email로 User 조회
     -> deleted=false 확인
     -> BCrypt password matches
     -> 기존 refreshToken 쿠키 정리 시도
     -> 새 UUID sid 생성
     -> Access/Refresh/MediaToken 생성
     -> Redis에 새 RefreshToken 세션 저장 및 최대 10개 정리
  -> AccessToken body 구성
  -> RefreshToken/MediaToken 쿠키 구성
  -> 200 OK
```

상세 순서는 다음과 같다.

1. `UserRepository.findByEmail`로 사용자를 조회한다.
2. 사용자가 없거나 `deleted=true`이면 외부에는 동일하게 `U003 USER_NOT_FOUND`를 반환한다.
3. `BCryptPasswordEncoder.matches(평문 요청 비밀번호, DB 해시)`로 비밀번호를 비교한다.
4. 불일치하면 `U004 INVALID_PASSWORD`를 반환한다.
5. 요청에 기존 `refreshToken` 쿠키가 있으면 파싱을 시도한다.
6. 정상 서명/미만료이며 `type=refresh`이면, 그 쿠키의 `sub`와 `sid`를 사용해 기존 `RT:{sid}`와 유저 세션 인덱스 member를 삭제한다.
7. 기존 쿠키 파싱이 `ErrorException`으로 실패하면 그 예외는 무시하고 새 로그인을 계속한다.
8. 기존 쿠키가 정상 JWT지만 `type=refresh`가 아니면 기존 세션 삭제 없이 계속한다.
9. 새 UUID `sid`를 만든다.
10. 같은 `userId`, `sid`로 10분 AccessToken, 7일 RefreshToken, 10분 MediaToken을 만든다.
11. 새 RefreshToken을 Redis에 저장하고 유저 세션 인덱스를 갱신한다.
12. 세션 수가 10개를 초과하면 오래된 세션을 정리한다.
13. 컨트롤러가 AccessToken은 body, RefreshToken과 MediaToken은 HttpOnly 쿠키로 반환한다.

### 7.3 기존 쿠키 정리의 정확한 의미

기존 쿠키 정리는 새로 로그인한 계정이 아니라 **기존 쿠키 Claims의 `sub`와 `sid`**를 기준으로 수행한다. 또한 Redis에 저장된 토큰과 쿠키가 현재 일치하는지는 확인하지 않고, 정상 서명된 RefreshToken인지 확인한 뒤 해당 key를 삭제한다.

따라서 이 동작은 같은 브라우저 쿠키 컨텍스트의 이전 세션을 가능한 범위에서 정리하는 기능이지, 해당 유저의 모든 세션을 제거하는 기능이 아니다. 다른 브라우저/기기의 `sid`는 유지된다. 기존 MediaToken은 서버 저장값이 없으며, 성공 응답이 같은 이름의 새 쿠키로 덮어쓴다.

### 7.4 트랜잭션 경계

`AuthService.login`에는 `@Transactional`이 없다. 사용자 조회, 토큰 생성, Redis 저장, HTTP 응답 생성은 하나의 DB/Redis 원자 트랜잭션이 아니다. 예를 들어 Redis 저장 뒤 응답 전달에 실패하면 클라이언트가 토큰을 받지 못했어도 서버에는 새 RefreshToken 세션이 남을 수 있다.

---

## 8. 보호 REST 요청의 AccessToken 인증 흐름

### 8.1 헤더 규칙

```http
Authorization: Bearer {accessToken}
```

`Bearer ` 접두어 비교는 대소문자를 포함해 코드 문자열 그대로 수행한다.

### 8.2 처리 순서

```text
보호 URL 요청
  -> authenticatedFilterChain
  -> JwtSecurityFilter
     -> Authorization 헤더 조회
     -> AccessTokenAuthenticator
        -> 헤더 존재 확인
        -> "Bearer " 접두어 확인
        -> JWT 서명/만료/형식 파싱
        -> type=access 확인
        -> sub -> userId, sid 추출
     -> SecurityContext에 AuthenticatedUser 저장
  -> Controller @AuthenticationPrincipal
  -> Service의 활성 사용자/도메인 권한 검증
```

`JwtSecurityFilter`는 인증 중 발생한 `ErrorException`을 직접 잡아 `ErrorResponse` JSON을 쓰고 요청을 종료한다. 이 경우 컨트롤러와 서비스는 호출되지 않는다.

AccessToken 인증 단계는 다음을 하지 않는다.

- Redis에서 `RT:{sid}` 존재 여부 확인
- 로그아웃 여부 확인
- 유저 DB 조회
- `deleted` 확인
- 친구/채팅방 권한 확인
- role/authority 구성

즉 로그아웃했거나 최대 세션 수 제한으로 RefreshToken이 제거된 `sid`의 AccessToken도 만료 전에는 필터를 통과할 수 있다. 이후 서비스가 활성 사용자와 도메인 권한을 확인해야 최종 작업이 허용된다.

---

## 9. 토큰 재발급 흐름

### 9.1 인터페이스

- Method/Path: `POST /refresh`
- 보안 체인: 공개
- Request body: 없음
- 입력: `refreshToken` 쿠키
- 성공 body:

```json
{
  "accessToken": "..."
}
```

- 성공 부가 응답: 회전된 `refreshToken`, 새 `mediaToken` Set-Cookie

### 9.2 서버 처리 순서

```text
refreshToken Cookie
  -> JWT 서명/만료/형식 파싱
  -> type=refresh 확인
  -> sid 추출
  -> Redis RT:{sid}와 요청 토큰 원문 일치 확인
  -> sub로 User 조회 및 deleted=false 확인
  -> 같은 sid로 Access/Refresh/MediaToken 재발급
  -> Redis RT:{sid}를 새 RefreshToken으로 교체
  -> USER_SESSIONS score와 TTL 갱신
  -> 새 AccessToken body + 두 쿠키 반환
```

상세 순서는 다음과 같다.

1. 쿠키를 `JwtUtils.parseClaims`로 파싱한다.
2. 쿠키가 없으면 null이 전달되어 `J004 JWT_EMPTY_TOKEN`이 된다.
3. 만료/서명/형식 오류는 각각 공통 JWT 오류로 반환한다.
4. JWT는 정상이지만 `type=refresh`가 아니면 `J007 JWT_INVALID_REFRESH_TOKEN`을 반환한다.
5. `sid`를 추출하고 Redis의 `RT:{sid}` 원문과 요청 쿠키 원문을 `String.equals`로 비교한다.
6. key가 없거나 값이 다르면 `J007`을 반환한다. 로그아웃, 세션 수 초과, Redis의 현재 원문과 다른 과거 토큰이 여기에 해당한다.
7. Claims의 `sub`로 사용자를 조회한다.
8. 사용자가 없거나 삭제됐으면 `U003 USER_NOT_FOUND`를 반환한다.
9. 새 `sid`를 만들지 않고 기존 `sid`를 유지한 채 세 토큰을 모두 다시 만든다.
10. 새 RefreshToken을 `RT:{sid}`에 덮어쓰고 TTL을 7일로 갱신한다.
11. 유저 세션 Sorted Set의 해당 `sid` score를 현재 시각으로 바꾸고 인덱스 TTL도 7일로 갱신한다.
12. 새 RefreshToken/MediaToken 쿠키와 AccessToken body를 반환한다.

재발급은 RefreshToken을 다시 생성해 Redis에 저장한다. 발급 초가 달라져 새 원문이 만들어진 일반적인 경우에는 이전 원문이 Redis와 달라져 다시 사용할 수 없다. 그러나 같은 초에는 `jti`/nonce가 없는 두 JWT가 동일할 수 있으므로 엄격한 single-use rotation은 보장하지 않는다. 비교와 저장도 원자 연산이 아니어서 동시 재발급은 클라이언트에서도 직렬화해야 한다.

`AuthService.refresh`에도 `@Transactional`은 없다.

---

## 10. 로그아웃 흐름

### 10.1 인터페이스

- Method/Path: `POST /logout`
- 보안 체인: 보호
- 입력: AccessToken 헤더
- 성공: HTTP 200, body 없음
- 성공 부가 응답: `refreshToken`, `mediaToken` 쿠키 `Max-Age=0`

### 10.2 서버 처리 순서

1. 보호 REST 필터가 AccessToken을 검증한다.
2. AccessToken의 `sub`와 `sid`로 `AuthenticatedUser`를 만든다.
3. 컨트롤러가 `AuthService.logout(userId, sid)`를 호출한다.
4. Redis의 `RT:{sid}`를 삭제한다.
5. `USER_SESSIONS:{userId}`에서 `sid`를 제거한다.
6. 컨트롤러가 RefreshToken과 MediaToken 쿠키를 만료시키는 두 `Set-Cookie` 헤더를 반환한다.

로그아웃 서비스는 유저 DB나 RefreshToken 쿠키를 조회하지 않는다. Redis에 해당 key가 없어도 인증된 AccessToken이면 삭제 작업 후 200을 반환한다.

### 10.3 로그아웃 이후 남는 상태

- 현재 `sid`의 RefreshToken은 Redis에 없으므로 재발급할 수 없다.
- 브라우저가 응답을 정상 반영하면 RefreshToken/MediaToken 쿠키가 삭제된다.
- 이미 발급된 AccessToken은 blacklist가 없으므로 만료까지 JWT 검증을 통과할 수 있다.
- MediaToken도 서버 저장값이 없어, 별도로 보관된 토큰 원문은 만료까지 JWT 검증을 통과할 수 있다.
- 이미 연결된 WebSocket은 강제로 종료되지 않는다.
- 남은 AccessToken으로 새로운 STOMP 연결을 시도해도 CONNECT 인증은 Redis logout 상태를 확인하지 않는다.

세 토큰은 같은 `sid`로 발급되는 것을 전제로 한다. AccessToken과 브라우저의 RefreshToken 쿠키가 서로 다른 세션에서 온 상태로 로그아웃하면, 서버는 AccessToken의 `sid`를 무효화하고 브라우저는 현재 쿠키를 삭제한다. 쿠키 쪽 다른 `sid`의 Redis 값은 이 요청만으로 삭제되지 않을 수 있으므로 프론트엔드는 서로 다른 로그인 세션의 토큰을 혼합하지 않아야 한다.

---

## 11. 회원 삭제와 전체 세션 제거

### 11.1 인터페이스와 처리

- Method/Path: `DELETE /me`
- 보안 체인: 보호
- 입력: AccessToken 헤더

처리 순서는 다음과 같다.

1. AccessToken의 `sub`로 현재 `userId`를 얻는다.
2. `UserService.deleteUser`가 사용자를 조회한다.
3. 사용자가 없거나 이미 삭제됐으면 `U003`을 반환한다.
4. 엔티티의 `deleted`를 `true`로 바꾼다.
5. `USER_SESSIONS:{userId}`에서 등록된 모든 `sid`를 읽는다.
6. 인덱스가 가리키는 `RT:{sid}`들과 유저 세션 인덱스를 삭제한다.
7. JPA transaction이 정상 종료되면 논리 삭제가 반영된다.

`UserService.deleteUser`는 `@Transactional`이지만 MySQL 변경과 Redis 명령을 하나의 분산 트랜잭션으로 묶는 설정은 없다. 두 저장소의 작업은 완전히 원자적이지 않다.

### 11.2 삭제 이후 토큰별 결과

| 상태 | 결과 |
|---|---|
| 인덱스에 등록된 RefreshToken | Redis에서 제거되어 재발급 불가. 보통 Redis 불일치 `J007`이 사용자 조회보다 먼저 발생한다. |
| 기존 AccessToken | 서명/만료만 보면 유효할 수 있으나, 각 도메인 서비스의 활성 사용자 검증에서 `U003`으로 차단된다. |
| 기존 MediaToken | 파일 요청 때 사용자 DB를 다시 조회하므로 `U003`으로 차단된다. |
| 기존 WebSocket | 연결 자체를 종료하지 않는다. 새 구독은 `deleted=false` 조건에서 거부되고 메시지 처리 서비스도 삭제 사용자를 거부하지만, 서버가 소켓을 즉시 disconnect하지는 않는다. |

현재 `DELETE /me` 응답은 RefreshToken/MediaToken 만료 쿠키를 내려주지 않는다. 즉 Redis 세션과 계정 상태는 무효화되지만 브라우저의 HttpOnly 쿠키 자체는 만료 시각까지 남을 수 있다.

Redis 명령의 부분 실패로 사용자 세션 인덱스에 없는 고아 `RT:{sid}`가 이미 생겼다면 회원 삭제가 그 key를 찾아 지우지는 못한다. 고아 토큰이 TTL 동안 Redis 일치 검사를 통과하더라도 다음 단계의 `User.deleted=true` 검사에서 `U003`으로 재발급이 차단된다.

---

## 12. MediaToken과 메시지 파일 조회

### 12.1 별도 토큰을 사용하는 이유와 경계

현재 메시지 파일 조회 경로는 다음과 같다.

```http
GET /media/messages/{chatMessageId}/files/{fileOrder}?storedFileVariant=ORIGINAL|THUMBNAIL
Cookie: mediaToken=...
```

`GET /media/**`는 Security filter chain에서는 공개다. 따라서 AccessToken 헤더나 `SecurityContext`에 의존하지 않고 `StoredFileService.findMessageFile`이 쿠키를 직접 검증한다. 일반 `<img src="...">`처럼 Authorization 헤더를 붙이기 어려운 브라우저 자원 요청을 쿠키로 인증할 수 있는 구조다.

### 12.2 서버 검증 순서

1. `mediaToken` 쿠키를 JWT로 파싱한다.
2. 서명, 만료, 형식을 검증한다.
3. `type=media`인지 확인한다. 다른 타입이면 `J009 JWT_INVALID_MEDIA_TOKEN`이다.
4. `sub`에서 `userId`를 얻고 사용자 존재 및 `deleted=false`를 확인한다.
5. `chatMessageId`로 메시지와 소속 채팅방을 조회한다.
6. 사용자가 그 채팅방의 `ChatRoomUser`인지 확인한다.
7. 참여 상태가 `LEFT`가 아닌지 확인한다.
8. `visibleStartMessageId <= chatMessageId`인지 확인해 사용자가 볼 수 있는 메시지 범위인지 검증한다.
9. `chat-message:{chatMessageId}`, `fileOrder`, `storedFileVariant`로 파일 메타데이터를 찾는다.
10. 실제 로컬 파일이 존재하는지 확인한 뒤 파일을 반환한다.

MediaToken 검증은 `sid`나 Redis의 RefreshToken 상태를 확인하지 않는다. 로그아웃은 브라우저 쿠키를 지우지만, 복사되었거나 별도로 남은 MediaToken 원문을 서버가 즉시 blacklist하지는 못한다. TTL은 10분이며 사용자 삭제와 채팅방 권한 변경은 매 파일 요청의 DB 검증으로 반영된다.

이미지/영상 응답은 `Cache-Control: private, max-age=600`과 `Vary: Cookie`를 설정한다. 일반 파일은 attachment로 반환한다. 캐시된 브라우저 자원은 서버 재검증 요청이 발생하기 전까지 클라이언트 캐시 정책의 영향을 받을 수 있다.

---

## 13. WebSocket/STOMP 인증과 인가

### 13.1 연결 단계

WebSocket/SockJS endpoint는 `/ws`다. HTTP GET 진입은 공개 체인으로 통과하지만, STOMP 세션 인증은 inbound channel의 `JwtChannelInterceptor`가 처리한다.

클라이언트는 STOMP `CONNECT` native header에 다음 값을 넣어야 한다.

```text
Authorization: Bearer {accessToken}
```

처리 순서는 다음과 같다.

1. `CONNECT` 프레임인지 확인한다.
2. 첫 번째 native `Authorization` 헤더를 읽는다.
3. REST와 동일한 `AccessTokenAuthenticator`로 Bearer 형식, JWT, `type=access`를 검증한다.
4. `sub`의 `userId`로 `StompPrincipal`을 만든다.
5. STOMP 세션의 user principal로 설정한다.

`StompPrincipal#getName()`은 `userId` 문자열이다. AccessToken의 `sid`는 WebSocket principal에 보관하지 않는다. 그러므로 같은 유저의 여러 로그인 세션/브라우저는 user destination 관점에서 같은 사용자 이름을 공유한다.

### 13.2 연결 이후

`JwtChannelInterceptor`는 `CONNECT`에서만 토큰을 검사한다. 연결 후 들어오는 `SEND`, `SUBSCRIBE`마다 JWT 만료나 Redis 세션 상태를 다시 확인하지 않는다.

결과적으로 다음이 현재 동작이다.

- 연결 시점에 만료된 AccessToken은 거부된다.
- 연결 후 AccessToken이 만료돼도 기존 STOMP 연결을 자동 종료하지 않는다.
- HTTP 로그아웃, RefreshToken 제거, 세션 수 초과가 기존 연결을 자동 종료하지 않는다.
- 재연결할 때는 그 시점의 유효한 최신 AccessToken이 필요하다.
- STOMP message handler는 principal의 `userId`를 서비스에 전달하고, 서비스가 삭제 사용자/채팅방 참여 상태를 다시 확인한다.

### 13.3 채팅방 Topic 구독 인가

`ChatRoomSubscriptionInterceptor`는 destination이 아래 정규식에 정확히 일치할 때만 추가 검증한다.

```text
^/topic/chatRooms/(\d+)$
```

검증 순서는 다음과 같다.

1. STOMP principal이 없으면 `W001 WEBSOCKET_UNAUTHENTICATED`를 발생시킨다.
2. principal의 `userId`와 destination의 `chatRoomId`를 얻는다.
3. `ChatRoomUserRepository.existsActiveMember`로 다음을 모두 확인한다.
   - 해당 채팅방/유저 관계가 존재함
   - 사용자 `deleted=false`
   - 참여 상태 `ACTIVE`
4. 실패하면 `/user/queue/errors`로 `CR010 CHAT_ROOM_ACCESS_DENIED`를 보내고 해당 `SUBSCRIBE` 메시지는 더 처리하지 않는다.

이 정규식에 해당하지 않는 다른 destination은 이 interceptor의 채팅방 멤버 검사를 받지 않는다. 각 message handler와 서비스의 별도 검증이 최종 권한 경계다.

### 13.4 STOMP 오류 전달

- `CONNECT` 등 프레임 처리 과정의 인증 예외는 `StompErrorHandler`가 STOMP `ERROR` 프레임 body에 `ErrorResponse` JSON을 넣어 반환한다.
- `@MessageMapping` 처리 중 `ErrorException`은 `StompMessageExceptionAdvice`가 현재 세션의 `/user/queue/errors`로 반환한다.
- 채팅방 Topic 구독 권한 부족도 `/user/queue/errors`로 전달하고 구독을 거부한다.
- STOMP에는 HTTP 응답 상태가 없지만 `ErrorResponse.status`에는 대응 HTTP 숫자가 들어간다.

---

## 14. 인증 이후 도메인 인가

JWT의 `sub`는 요청자를 식별할 뿐, 다음 권한을 증명하지 않는다.

- 계정이 현재 존재하고 삭제되지 않았는지
- 친구 관계의 소유자인지
- 채팅방의 현재 멤버인지
- 채팅방에서 나간 상태인지
- 방장 권한이 필요한 작업인지
- 특정 메시지가 사용자의 가시 범위에 포함되는지

현재 user, friend, chat, file 서비스는 해당 유스케이스에 맞게 사용자와 관계 데이터를 다시 조회한다. 특히 로그아웃/회원 삭제 직후에도 AccessToken 자체는 stateless하게 파싱될 수 있으므로 이 검증이 실제 권한 차단선이다.

인증 필터에서 사용자 DB를 조회하지 않는 이유로 보호 API는 일반적으로 두 단계로 이해해야 한다.

```text
1단계 인증: 이 요청이 서버가 서명한 미만료 AccessToken을 가졌는가?
2단계 인가: 그 userId가 현재 활성 상태이고 이 도메인 객체에 작업할 권한이 있는가?
```

---

## 15. 오류 응답

### 15.1 공통 형태

REST와 STOMP 오류 body는 같은 `ErrorResponse` 구조를 사용한다.

```json
{
  "code": "J002",
  "status": 401,
  "message": "만료된 JWT 토큰입니다.",
  "timestamp": "2026-08-18 12:34:56"
}
```

### 15.2 인증 관련 코드

| 코드 | HTTP | 현재 발생 조건 |
|---|---:|---|
| `C001` | 400 | 로그인 DTO validation 실패 |
| `U003` | 404 | 로그인/재발급/미디어 또는 도메인 처리 중 사용자가 없거나 삭제됨 |
| `U004` | 401 | 로그인 비밀번호 불일치 |
| `J001` | 401 | JWT 서명 오류 또는 malformed token. AccessToken 사용처에서 토큰 타입이 access가 아닌 경우에도 사용 |
| `J002` | 401 | JWT 만료 |
| `J003` | 401 | 지원하지 않는 JWT |
| `J004` | 401 | null/빈 JWT. RefreshToken 또는 MediaToken 쿠키 누락도 이 경로로 처리됨 |
| `J005` | 401 | 보호 REST 또는 STOMP CONNECT에 `Authorization` 헤더 없음 |
| `J006` | 401 | `Authorization` 값이 정확한 `Bearer ` 형식으로 시작하지 않음 |
| `J007` | 401 | 재발급 요청 JWT가 RefreshToken 타입이 아니거나 Redis 현재 값과 불일치 |
| `J009` | 401 | 메시지 파일 조회 JWT가 MediaToken 타입이 아님 |
| `W001` | 401 | 채팅방 Topic 구독 시 STOMP principal 없음 |
| `CR010` | 403 | 채팅방 Topic 구독 또는 메시지 파일 조회에서 채팅방 접근 불가 |
| `S001` | 500 | 처리되지 않은 서버 예외 |

`J008 ACCESS_TOKEN_MISMATCH`는 enum에 선언되어 있지만 현재 인증 흐름에서 사용하는 코드가 없다. AccessToken은 Redis 현재 값과 비교하지 않는다.

### 15.3 예외가 처리되는 위치

- 보호 REST JWT 오류: `JwtSecurityFilter`가 직접 JSON 응답 후 chain 중단
- 컨트롤러/서비스의 `ErrorException`: `GlobalExceptionHandler`
- DTO validation: `GlobalExceptionHandler`가 첫 번째 field error 메시지 사용
- 처리되지 않은 REST 예외: `S001`
- STOMP 프레임 처리 오류: `StompErrorHandler`의 `ERROR` 프레임
- STOMP message handler 오류: `/user/queue/errors`

---

## 16. 프론트엔드 관리 책임

이 절은 서버 내부 동작과 분리해, 현재 API를 사용하는 프론트엔드가 반드시 맞춰야 하는 상태 관리 규칙만 정리한다.

### 16.1 로그인과 토큰 보관

- 로그인 응답 body의 `accessToken`을 보호 REST 요청과 STOMP `CONNECT`에 사용한다.
- RefreshToken과 MediaToken은 HttpOnly이므로 값을 읽거나 애플리케이션 저장소로 복사하려 하지 않는다.
- 쿠키를 주고받는 cross-origin Fetch에는 `credentials: "include"`, Axios에는 `withCredentials: true`가 필요하다.
- AccessToken, RefreshToken, MediaToken은 같은 로그인/재발급 응답에서 나온 세트로 유지해야 한다. 다른 브라우저 세션의 AccessToken을 섞으면 로그아웃 대상 `sid`와 쿠키 `sid`가 어긋날 수 있다.
- `sid`를 프론트 권한 판단에 사용하지 않는다. 서버 세션 식별용 claim이다.

### 16.2 로그인 상태 확인

현재 `/login-status` endpoint는 없다. 로그인 상태 확인은 다음 서버 동작을 조합해야 한다.

1. 메모리에 사용 가능한 AccessToken이 있으면 보호 API인 `GET /me`를 호출한다.
2. AccessToken이 없거나 만료됐다면 쿠키를 포함해 `POST /refresh`를 한 번 시도한다.
3. 성공하면 body의 새 AccessToken을 저장하고, 브라우저가 갱신된 두 쿠키를 반영하게 한다.
4. 재발급 실패 시 로그인되지 않은 상태로 전환하고 로컬 AccessToken을 제거한다.

JWT payload를 프론트에서 decode하는 것은 만료 예정 시각을 예측하는 보조 수단일 수 있지만, Redis RefreshToken 상태나 사용자 삭제 상태를 증명하지 않으므로 서버 응답을 최종 기준으로 삼아야 한다.

### 16.3 401과 재발급

- 보호 API의 AccessToken 만료 등 재시도 가능한 인증 실패에 대해 `POST /refresh` 후 원 요청을 한 번 재시도한다.
- 여러 API가 동시에 401을 받더라도 재발급은 하나만 실행하고 나머지는 같은 Promise/결과를 기다리게 한다.
- 재발급은 RefreshToken을 다시 생성해 Redis에 저장한다. 병렬 결과가 서로 다른 원문이면 먼저 받은 값이 최종 Redis 값과 달라질 수 있고, 같은 초에 동일 원문이 생성되면 두 요청 모두 같은 쿠키 값을 받을 수도 있다.
- `/refresh` 자체가 실패하면 무한 재시도하지 않고 AccessToken을 폐기하고 로그인 화면으로 전환한다.
- `U003`처럼 계정이 없거나 삭제된 응답은 단순 AccessToken 갱신으로 해결되지 않는다.

### 16.4 로그아웃

- `POST /logout`에 현재 AccessToken 헤더를 붙이고, 쿠키 응답을 반영할 수 있도록 credentials를 포함한다.
- 성공 여부를 처리한 뒤 프론트가 보관한 AccessToken을 제거하고 WebSocket 연결을 종료한다.
- AccessToken이 이미 만료되면 `/logout`은 보호 체인에서 거부된다. 서버 세션까지 정리하려면 먼저 재발급해 유효한 AccessToken을 얻은 뒤 로그아웃해야 한다.
- UI에서 로컬 AccessToken만 지우는 것은 서버 로그아웃이 아니다. Redis RefreshToken은 만료 또는 세션 정리 전까지 남는다.

### 16.5 회원 삭제

- `DELETE /me` 성공 후 로컬 AccessToken과 로그인 UI 상태를 즉시 제거하고 WebSocket을 종료한다.
- 현재 삭제 응답은 HttpOnly 쿠키를 만료시키지 않는다. 프론트 JavaScript는 이 쿠키를 직접 지울 수 없다는 점을 고려해야 한다.
- 쿠키까지 즉시 지워야 하는 UX라면, 아직 유효한 AccessToken을 유지한 상태에서 삭제 성공 후 `/logout`을 호출해 만료 Set-Cookie를 받거나 서버 API가 삭제 응답에서도 쿠키를 만료시키도록 별도 변경해야 한다.

### 16.6 메시지 파일

- `<img src>` 또는 브라우저 자원 URL로 `/media/**`를 사용하면 브라우저가 MediaToken 쿠키를 보낼 수 있는 origin/site 정책이어야 한다.
- Fetch로 파일을 조회할 때는 credentials를 포함한다.
- MediaToken이 만료되면 AccessToken만 새로 바꿔서는 해결되지 않는다. `/refresh`가 새 MediaToken 쿠키도 함께 발급한다.
- `SameSite=Lax`, `Secure=false`인 현재 쿠키 속성과 실제 프론트/백엔드 배포 도메인이 호환되는지 확인해야 한다. 브라우저의 cross-site cookie 정책은 CORS 허용과 별개다.

### 16.7 WebSocket

- SockJS/WebSocket transport 연결 뒤 STOMP `CONNECT` native header에 AccessToken을 넣는다.
- HTTP `Authorization` 헤더만 핸드셰이크에 넣고 STOMP header를 생략하면 principal이 만들어지지 않는다.
- 반대로 STOMP native header만 있어도 현재 GET 전용 whitelist 밖의 SockJS POST fallback은 HTTP 보호 체인을 통과하지 못할 수 있다. 실제 배포에서 선택되는 transport와 HTTP header 지원을 확인한다.
- 재발급으로 AccessToken이 바뀌어도 이미 연결된 STOMP 세션 principal은 자동 갱신되지 않는다.
- 기존 연결은 토큰 만료/로그아웃 때 자동 종료되지 않으므로 프론트가 명시적으로 끊는다.
- reconnect 시에는 가장 최근 AccessToken을 사용한다.
- 연결 직후 `/user/queue/errors`를 구독해 구독/메시지 처리 오류를 수신한다.

---

## 17. 현재 구현의 운영상 주의점

1. AccessToken blacklist가 없으므로 로그아웃과 Redis 세션 제거는 AccessToken을 즉시 폐기하지 않는다.
2. MediaToken도 stateless라서 로그아웃 시 브라우저 쿠키 삭제 외 서버 측 즉시 폐기 수단이 없다.
3. RefreshToken 재발급은 exact-value 비교를 하지만 비교와 교체가 원자적이지 않고, 같은 초에는 새 JWT 원문이 이전 것과 같을 수 있다.
4. 최대 10세션 제한은 RefreshToken 재발급 권한을 제한하며 기존 Access/MediaToken이나 WebSocket을 끊지 않는다.
5. 회원 삭제는 사용자 세션 인덱스에 등록된 RefreshToken을 제거하지만 고아 key를 열거하지 못하며, 삭제 응답에서 쿠키도 만료시키지 않는다.
6. WebSocket은 CONNECT 때만 AccessToken을 검증하고 `sid`를 보관하지 않는다.
7. `GET /ws/**`만 공개되어 있어 SockJS의 non-GET transport 사용 여부를 실제 환경에서 확인해야 한다.
8. REST는 특정 두 Origin만 credential CORS를 허용하지만 WebSocket endpoint Origin은 `*` 패턴이다.
9. 두 쿠키는 `Secure=false`, `SameSite=Lax`다. 운영 HTTPS 및 cross-site 배포 정책과 일치하는지 확인해야 한다.
10. `/login-status`, 전체 세션 목록, 특정 원격 세션 강제 로그아웃 API는 현재 없다.
11. 로그인 시 기존 RefreshToken 쿠키 정리는 Redis 값 일치 검증 없이 쿠키 Claims의 `sub`, `sid`를 기준으로 삭제한다.
12. 로그인/재발급/로그아웃에는 서비스 수준 DB transaction이 없고, Redis의 복합 연산도 원자적이지 않다.

---

## 18. 구현 근거 파일

인증 흐름을 변경할 때 최소한 다음 파일을 함께 확인해야 한다.

- `src/main/java/com/tgg/chat/common/security/config/SecurityConfig.java`
- `src/main/java/com/tgg/chat/common/security/config/SecurityWhitelist.java`
- `src/main/java/com/tgg/chat/common/security/config/PasswordEncoderConfig.java`
- `src/main/java/com/tgg/chat/common/security/jwt/JwtUtils.java`
- `src/main/java/com/tgg/chat/common/security/jwt/AccessTokenAuthenticator.java`
- `src/main/java/com/tgg/chat/common/security/jwt/JwtSecurityFilter.java`
- `src/main/java/com/tgg/chat/common/security/principal/AuthenticatedUser.java`
- `src/main/java/com/tgg/chat/common/security/token/RedisTokenStore.java`
- `src/main/java/com/tgg/chat/domain/auth/controller/AuthController.java`
- `src/main/java/com/tgg/chat/domain/auth/service/AuthService.java`
- `src/main/java/com/tgg/chat/domain/auth/dto/**`
- `src/main/java/com/tgg/chat/common/messaging/config/WebSocketConfig.java`
- `src/main/java/com/tgg/chat/common/messaging/stomp/JwtChannelInterceptor.java`
- `src/main/java/com/tgg/chat/common/messaging/stomp/ChatRoomSubscriptionInterceptor.java`
- `src/main/java/com/tgg/chat/common/messaging/stomp/StompErrorHandler.java`
- `src/main/java/com/tgg/chat/common/messaging/stomp/StompMessageExceptionAdvice.java`
- `src/main/java/com/tgg/chat/domain/file/controller/StoredFileController.java`
- `src/main/java/com/tgg/chat/domain/file/service/StoredFileService.java`
- `src/main/java/com/tgg/chat/domain/user/service/UserService.java`
- `src/main/java/com/tgg/chat/exception/ErrorCode.java`
- `src/main/java/com/tgg/chat/exception/GlobalExceptionHandler.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-docker.yml`

인증/보안 테스트는 다음 파일에서 현재 계약을 확인한다.

- `src/test/java/com/tgg/chat/common/security/config/SecurityConfigTest.java`
- `src/test/java/com/tgg/chat/common/security/jwt/AccessTokenAuthenticatorTest.java`
- `src/test/java/com/tgg/chat/common/security/jwt/JwtSecurityFilterTest.java`
- `src/test/java/com/tgg/chat/common/security/jwt/JwtUtilsTest.java`
- `src/test/java/com/tgg/chat/domain/auth/controller/AuthControllerTest.java`
- `src/test/java/com/tgg/chat/domain/auth/service/AuthServiceTest.java`
- `src/test/java/com/tgg/chat/domain/user/service/UserServiceTest.java`
