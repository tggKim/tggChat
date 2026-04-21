# AUTHFLOW.md

## 목적
- 이 문서는 현재 인증 토큰 관리 전략과 요청 흐름을 정리한다.
- 이 프로젝트는 유저당 하나의 `AccessToken` / `RefreshToken` 세트만 유효하도록 관리한다.
- 즉, 한 유저는 동시에 하나의 세션만 로그인 상태를 유지할 수 있다.

## 토큰 관리 전략
- 로그인 시 새로운 `accessToken`, `refreshToken`을 발급한다.
- Redis에는 유저별로 하나의 토큰 세트만 저장한다.
- 같은 유저가 다른 기기나 브라우저에서 다시 로그인하면 기존 Redis 토큰은 새 토큰으로 덮어써진다.
- 따라서 이전 세션이 가지고 있던 토큰은 Redis 값과 불일치하게 되어 더 이상 유효하지 않다.

## Redis Key 구조
- `AT:{userId}` = `accessToken`
- `RT:{userId}` = `refreshToken`

## 요청 흐름

### 1. 로그인
- 대상 엔드포인트: `/login`
- 동작:
- `accessToken`, `refreshToken` 발급
- Redis의 `AT:{userId}`, `RT:{userId}`에 각각 저장
- 기존 값이 있으면 새 값으로 덮어씀
- 결과:
- 같은 유저의 기존 로그인 세션은 더 이상 유효하지 않다.

### 2. 로그인 여부 확인
- 대상 엔드포인트: `/login-status`
- 동작:
- `userId` 기준으로 Redis의 `RT:{userId}` 존재 여부 확인
- 존재하면 로그인 상태로 판단
- 없으면 로그아웃 상태로 판단
- 목적:
- 프론트에서 유저의 다른 세션 로그인 여부를 파악하는 데 사용

### 3. AccessToken으로 요청
- 동작:
- 먼저 AccessToken 자체의 유효성과 만료 여부 확인
- 검증 통과 후 토큰의 `userId`로 Redis의 `AT:{userId}` 조회
- Redis에 저장된 값과 현재 요청의 AccessToken이 일치하는지 확인
- 결과:
- 일치하면 요청 허용
- 값이 없거나 다르면 다른 세션 로그인, 로그아웃, 재로그인 등으로 인해 무효화된 토큰으로 판단

### 4. RefreshToken으로 재발급 요청
- 대상 엔드포인트: `/refresh`
- 동작:
- RefreshToken 유효성 검증
- 토큰의 `userId`로 Redis의 `RT:{userId}` 조회
- Redis 값이 존재하고 현재 RefreshToken과 일치하는지 확인
- 검증 통과 시 새 `accessToken`, `refreshToken` 발급
- Redis의 `AT:{userId}`, `RT:{userId}`를 새 값으로 갱신
- 결과:
- 값이 없거나 다르면 만료, 로그아웃, 또는 다른 세션 로그인으로 인해 무효한 토큰으로 판단

### 5. 로그아웃 또는 유저 삭제
- 대상 엔드포인트: `/logout`, `/user`
- 동작:
- Redis의 `AT:{userId}`, `RT:{userId}` 삭제
- 결과:
- 이후 기존 AccessToken, RefreshToken은 더 이상 사용할 수 없다.

## 기대 효과
- 유저당 하나의 세션만 유지할 수 있다.
- 다른 세션에서 다시 로그인하면 이전 세션 토큰이 자동으로 무효화된다.
- Redis를 기준으로 토큰 일치 여부를 검사하므로 단일 세션 정책을 강제할 수 있다.
