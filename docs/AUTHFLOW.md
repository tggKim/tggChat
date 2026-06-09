# AUTHFLOW.md

## 목적
- 이 문서는 현재 인증 토큰 관리 전략과 요청 흐름을 정리한다.
- 이 프로젝트는 `AccessToken`과 `RefreshToken`을 사용한다.
- `AccessToken`은 요청 인증에 사용하고, `RefreshToken`은 AccessToken 재발급에 사용한다.
- 현재 구조는 `sid` 기반 세션 식별자를 사용하여 여러 로그인 세션을 구분한다.

## 토큰 관리 전략
- 로그인 성공 시 `accessToken`, `refreshToken`을 새로 발급한다.
- `accessToken`은 응답 본문으로 전달하고, 클라이언트가 `Authorization: Bearer {accessToken}` 형식으로 사용한다.
- `refreshToken`은 HttpOnly 쿠키로 전달한다.
- `accessToken`과 `refreshToken`에는 공통으로 `sub`, `sid`, `type`, `iat`, `exp`를 포함한다.
- `sub`는 유저 ID, `sid`는 로그인 세션 식별자, `type`은 `access` 또는 `refresh`를 의미한다.
- Redis에는 `refreshToken`만 저장한다.
- `accessToken`은 Redis에 저장하지 않고, JWT 자체의 서명과 만료 시간을 기준으로 검증한다.
- 유저 존재 여부, 삭제 여부, 도메인 권한 검증은 서비스 계층에서 수행한다.

## Redis Key 구조
- `RT:{sid}` = `refreshToken`
- Redis 값은 해당 `sid` 세션의 현재 유효한 refreshToken이다.
- Redis TTL은 refreshToken 만료 시간과 동일하게 설정한다.
- 같은 `sid`로 재발급하면 Redis의 refreshToken 값을 새 값으로 갱신한다.

## 요청 흐름

### 1. 로그인
- 대상 엔드포인트: `POST /login`
- 동작:
- 이메일로 유저를 조회한다.
- 유저가 존재하지 않거나 삭제된 유저이면 실패한다.
- 비밀번호를 검증한다.
- 기존 refreshToken 쿠키가 존재하면 파싱을 시도한다.
- 기존 refreshToken이 정상적인 refreshToken이면 해당 `sid`의 Redis refreshToken을 삭제한다.
- 기존 쿠키 파싱 실패는 새 로그인을 막지 않는다.
- 새로운 `sid`를 생성한다.
- 새로운 `accessToken`, `refreshToken`을 발급한다.
- Redis에 `RT:{sid}` 키로 refreshToken을 저장한다.
- refreshToken은 HttpOnly 쿠키로 내려주고, accessToken은 응답 본문으로 내려준다.
- 결과:
- 새 로그인 세션이 생성된다.
- 기존 브라우저 쿠키에 있던 refreshToken 세션은 가능한 경우 정리된다.

### 2. AccessToken으로 요청
- 동작:
- `Authorization` 헤더가 존재하는지 확인한다.
- `Bearer` 형식인지 확인한다.
- accessToken의 서명, 만료 시간, 지원 형식, 토큰 타입을 검증한다.
- 토큰 타입이 `access`가 아니면 실패한다.
- 검증에 성공하면 `sub`와 `sid`를 추출하여 인증 주체를 생성한다.
- 결과:
- 필터 또는 인터셉터는 토큰 자체의 유효성만 검증한다.
- 유저 존재 여부, 삭제 여부, 기능 수행 권한은 각 서비스에서 추가로 검증한다.

### 3. RefreshToken으로 재발급 요청
- 대상 엔드포인트: `POST /refresh`
- 동작:
- refreshToken 쿠키를 읽는다.
- refreshToken의 서명, 만료 시간, 지원 형식, 토큰 타입을 검증한다.
- 토큰 타입이 `refresh`가 아니면 실패한다.
- refreshToken에서 `sid`를 추출한다.
- Redis의 `RT:{sid}` 값과 현재 refreshToken이 일치하는지 확인한다.
- Redis 값이 없거나 일치하지 않으면 실패한다.
- `sub`로 유저를 조회한다.
- 유저가 존재하지 않거나 삭제된 유저이면 실패한다.
- 같은 `sid`로 새로운 accessToken과 refreshToken을 발급한다.
- Redis의 `RT:{sid}` 값을 새 refreshToken으로 갱신한다.
- 새 refreshToken은 HttpOnly 쿠키로 내려주고, 새 accessToken은 응답 본문으로 내려준다.
- 결과:
- refreshToken이 회전된다.
- 이전 refreshToken은 Redis 값과 불일치하게 되어 더 이상 사용할 수 없다.

### 4. 로그아웃
- 대상 엔드포인트: `POST /logout`
- 동작:
- accessToken 인증을 통해 현재 요청의 `sid`를 추출한다.
- Redis에서 `RT:{sid}`를 삭제한다.
- refreshToken 쿠키를 만료시키는 `Set-Cookie` 헤더를 내려준다.
- 결과:
- 현재 세션의 refreshToken은 더 이상 재발급에 사용할 수 없다.
- 이미 발급된 accessToken은 서버에 저장하지 않으므로 남은 만료 시간 동안 JWT 자체는 유효할 수 있다.
- 클라이언트는 로그아웃 시 보관 중인 accessToken을 폐기해야 한다.

### 5. 유저 삭제
- 대상 엔드포인트: `DELETE /me`
- 동작:
- accessToken 인증을 통해 로그인 유저 ID를 추출한다.
- 로그인 유저가 존재하고 삭제되지 않았는지 검증한다.
- 유저를 삭제 상태로 변경한다.
- 결과:
- 삭제된 유저는 refreshToken 재발급에 실패한다.
- 삭제된 유저는 서비스 계층의 active user 검증을 통과할 수 없다.
- 서버가 모든 기존 세션의 refreshToken을 즉시 찾아 삭제하지는 않는다.
- 단, 보호 API와 WebSocket 메시징 로직에서는 삭제 유저 검증을 일관되게 수행해야 한다.

## 기대 효과
- accessToken은 짧은 만료 시간 동안 stateless하게 검증한다.
- refreshToken은 Redis에 저장하여 재발급 가능 여부를 서버에서 제어한다.
- `sid`를 통해 로그인 세션 단위로 refreshToken을 구분할 수 있다.
- 로그아웃 시 현재 세션의 refreshToken을 무효화할 수 있다.
- refreshToken 재사용은 Redis 값 불일치로 차단된다.
- 유저 삭제 후에는 refreshToken 재발급과 서비스 기능 사용이 차단된다.

## 주의사항
- accessToken은 Redis에 저장하지 않으므로 로그아웃이나 유저 삭제 직후에도 만료 전 accessToken 자체는 서명 검증을 통과할 수 있다.
- 따라서 모든 보호 서비스는 유저 존재 여부와 삭제 여부를 직접 검증해야 한다.