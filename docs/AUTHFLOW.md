# AUTHFLOW.md

## 목적
- 현재 인증 토큰 관리 전략과 요청 흐름을 정리한다.
- 이 프로젝트는 `AccessToken`과 `RefreshToken`을 사용한다.
- `AccessToken`은 요청 인증에 사용하고, `RefreshToken`은 AccessToken 재발급에 사용한다.
- 현재 구조는 `sid` 기반 세션 식별자를 사용하여 여러 로그인 세션을 구분한다.

## 토큰 관리 전략
- 로그인 성공 시 `accessToken`, `refreshToken`을 새로 발급한다.
- `accessToken`은 응답 바디로 전달하고, 클라이언트는 `Authorization: Bearer {accessToken}` 형식으로 사용한다.
- `refreshToken`은 HttpOnly 쿠키로 전달한다.
- `accessToken`과 `refreshToken`에는 공통으로 `sub`, `sid`, `type`, `iat`, `exp`를 포함한다.
- `sub`는 유저 ID, `sid`는 로그인 세션 식별자, `type`은 `access` 또는 `refresh`를 의미한다.
- `accessToken`은 Redis에 저장하지 않고 JWT 자체의 서명, 만료 시간, 토큰 타입을 기준으로 검증한다.
- `refreshToken`은 Redis에 저장하여 재발급 가능 여부를 서버에서 제어한다.
- 유저 존재 여부, 삭제 여부, 기능 수행 권한은 각 서비스 계층에서 추가로 검증한다.

## Redis Key 구조
- `RT:{sid}` = 해당 sid 세션의 현재 유효한 RefreshToken
- `USER_SESSIONS:{userId}` = 해당 유저의 sid 목록을 저장하는 Sorted Set
- `USER_SESSIONS:{userId}`의 score는 세션 저장 또는 갱신 시점의 시간값이다.
- `RT:{sid}`와 `USER_SESSIONS:{userId}`는 RefreshToken 만료 시간과 동일한 TTL을 가진다.
- 유저별 세션 수가 제한 개수를 초과하면 가장 오래된 sid부터 제거한다.
- 오래된 sid가 제거되면 해당 sid의 `RT:{sid}`도 함께 삭제한다.

## 요청 흐름

### 1. 로그인
- 대상 엔드포인트: `POST /login`

동작:
1. 요청값을 검증한다.
2. 이메일로 유저를 조회한다.
3. 유저가 존재하지 않거나 삭제된 유저면 실패한다.
4. 비밀번호를 검증한다.
5. 기존 RefreshToken 쿠키가 존재하면 파싱을 시도한다.
6. 기존 쿠키가 정상적인 RefreshToken이면 해당 토큰의 `sub`, `sid`로 기존 Redis 세션을 삭제한다.
7. 기존 쿠키 파싱 실패는 로그인 흐름을 막지 않는다.
8. 새로운 `sid`를 생성한다.
9. 새로운 AccessToken과 RefreshToken을 발급한다.
10. Redis에 `RT:{sid}`로 RefreshToken을 저장한다.
11. Redis의 `USER_SESSIONS:{userId}`에 sid를 추가하고 score를 현재 시간으로 저장한다.
12. 유저별 세션 수가 제한 개수를 초과하면 오래된 세션을 제거한다.
13. RefreshToken은 HttpOnly 쿠키로 내려주고, AccessToken은 응답 바디로 내려준다.

결과:
1. 새 로그인 세션이 생성된다.
2. 같은 브라우저에 남아 있던 이전 RefreshToken 세션은 가능한 경우 정리된다.
3. 유저의 세션 수가 제한 개수를 초과하면 오래된 세션부터 재발급이 불가능해진다.

### 2. AccessToken 인증 요청

동작:
1. `Authorization` 헤더 존재 여부를 확인한다.
2. `Bearer` 형식인지 확인한다.
3. AccessToken의 서명, 만료 시간, 지원 형식, 토큰 타입을 검증한다.
4. 토큰 타입이 `access`가 아니면 실패한다.
5. 검증에 성공하면 `sub`와 `sid`를 추출하여 인증 주체를 생성한다.
6. 생성된 인증 주체를 `SecurityContextHolder`에 저장한다.

결과:
1. 필터는 토큰 자체의 유효성만 검증한다.
2. 유저 존재 여부, 삭제 여부, 기능 수행 권한은 각 서비스에서 추가로 검증한다.

### 3. 토큰 재발급
- 대상 엔드포인트: `POST /refresh`

동작:
1. RefreshToken 쿠키를 읽는다.
2. RefreshToken의 서명, 만료 시간, 지원 형식, 토큰 타입을 검증한다.
3. 토큰 타입이 `refresh`가 아니면 실패한다.
4. RefreshToken에서 `sid`를 추출한다.
5. Redis의 `RT:{sid}` 값과 현재 RefreshToken이 일치하는지 확인한다.
6. Redis 값이 없거나 일치하지 않으면 실패한다.
7. RefreshToken의 `sub`로 유저를 조회한다.
8. 유저가 존재하지 않거나 삭제된 유저면 실패한다.
9. 같은 `sid`로 새로운 AccessToken과 RefreshToken을 발급한다.
10. Redis의 `RT:{sid}` 값을 새 RefreshToken으로 변경 후 TTL 갱신한다.
11. Redis의 `USER_SESSIONS:{userId}`에서 sid score와 TTL을 갱신한다.
12. 새 RefreshToken은 HttpOnly 쿠키로 내려주고, 새 AccessToken은 응답 바디로 내려준다.

결과:
1. RefreshToken이 회전된다.
2. 이전 RefreshToken은 Redis 값과 불일치하게 되어 더 이상 사용할 수 없다.
3. 해당 sid 세션의 최근 사용 시간이 갱신된다.

### 4. 로그아웃
- 대상 엔드포인트: `POST /logout`

동작:
1. AccessToken 인증을 통해 현재 유저 ID와 `sid`를 추출한다.
2. Redis에서 `RT:{sid}`를 삭제한다.
3. Redis의 `USER_SESSIONS:{userId}`에서 sid를 제거한다.
4. RefreshToken 쿠키를 만료시키는 `Set-Cookie` 헤더를 내려준다.

결과:
1. 현재 세션의 RefreshToken은 더 이상 재발급에 사용할 수 없다.
2. 이미 발급된 AccessToken은 서버가 저장하지 않으므로 남은 만료 시간 동안 JWT 자체의 서명 검증은 통과할 수 있다.
3. 클라이언트는 로그아웃 후 보관 중인 AccessToken을 제거해야 한다.

### 5. 유저 삭제
- 대상 엔드포인트: `DELETE /me`

동작:
1. AccessToken 인증을 통해 로그인 유저 ID를 추출한다.
2. 로그인 유저가 존재하고 삭제되지 않았는지 검증한다.
3. 유저를 삭제 상태로 변경한다.
4. Redis의 `USER_SESSIONS:{userId}`에서 해당 유저의 sid 목록을 조회한다.
5. 각 sid에 해당하는 `RT:{sid}`를 삭제한다.
6. `USER_SESSIONS:{userId}`를 삭제한다.

결과:
1. 삭제된 유저의 모든 RefreshToken 세션은 재발급에 사용할 수 없다.
2. 삭제된 유저는 서비스 계층의 active user 검증을 통과할 수 없다.
3. AccessToken 자체는 만료 전까지 서명 검증을 통과할 수 있으므로 보호 API와 WebSocket 메시지 로직에서는 유저 삭제 여부를 계속 검증해야 한다.

## 기대 효과
- AccessToken은 짧은 만료 시간 동안 stateless하게 검증한다.
- RefreshToken은 Redis에 저장하여 재발급 가능 여부를 서버에서 제어한다.
- `sid`를 통해 로그인 세션 단위로 RefreshToken을 구분할 수 있다.
- 로그아웃 시 현재 세션의 RefreshToken만 무효화할 수 있다.
- 유저 삭제 시 해당 유저의 모든 RefreshToken 세션을 제거할 수 있다.
- 유저별 최대 세션 수를 제한하여 RefreshToken이 무제한으로 쌓이는 것을 방지한다.
- RefreshToken 재사용은 Redis 값 불일치로 차단된다.

## 주의사항
- AccessToken은 Redis에 저장하지 않으므로 로그아웃이나 유저 삭제 직후에도 만료 전 AccessToken 자체의 서명 검증은 통과할 수 있다.
- 따라서 모든 보호 서비스는 유저 존재 여부와 삭제 여부를 직접 검증해야 한다.
- WebSocket 연결과 메시지 처리에서도 토큰 유효성만 믿지 말고, 필요한 경우 채팅방 멤버 여부와 유저 삭제 여부를 별도로 검증해야 한다.