# USERFLOW.md

## 1. 문서 목적과 코드 기준

- 사용자 생성, 공개 조회, 본인 조회, 사용자명 수정, 소프트 삭제의 현재 서버 흐름을 코드 기준으로 설명한다.
- `User.profileImageKey`를 변경하는 API는 파일 도메인에 구현되어 있으므로 프로필 이미지 교체·조회 흐름도 사용자 생명주기의 일부로 함께 다룬다.
- 주 기준 코드는 `UserController`, `UserService`, `UserRepository`, `User`, 사용자 request/response DTO다.
- 인증 연계는 `SecurityConfig`, `JwtSecurityFilter`, `AccessTokenAuthenticator`, `AuthService`, `RedisTokenStore`를 기준으로 한다.
- 실시간 사용자 정보 전파는 `UserMetadataEvent`, `RedisPublisher`, `RedisSubscriber`, `WebSocketConfig`까지 추적한다.
- 프로필 파일 세부 흐름은 `StoredFileController`, `StoredFileService`, `StoredFileRepository`, `StoredFile`을 기준으로 한다.
- 토큰 생성·재발급·쿠키의 전체 규칙은 [AUTHFLOW.md](./AUTHFLOW.md), 프로필 및 채팅 파일의 전체 흐름은 [FILEFLOW.md](./FILEFLOW.md), 채팅방별 삭제 사용자 처리의 상세는 [CHATFLOW.md](./CHATFLOW.md)를 함께 참고한다.

## 2. 사용자 영속 모델과 상태

### 2.1 `User` 필드

| 필드 | JPA 매핑/제약 | 용도 |
| --- | --- | --- |
| `userId` | `IDENTITY` PK | JWT `sub`와 서버 내부 사용자 식별자 |
| `email` | `UNIQUE`, `NOT NULL`, 최대 254자 | 가입·로그인 식별자 |
| `password` | `NOT NULL` | BCrypt로 인코딩된 비밀번호 저장 |
| `username` | `UNIQUE`, `NOT NULL`, 최대 50자 | 화면 표시명, 친구 추가 검색값 |
| `deleted` | `NOT NULL` | active/soft-deleted 판정 |
| `profileImageKey` | nullable, 최대 255자 | 프로필 원본·썸네일을 찾는 논리 key |
| `createdAt` | 생성 감사 필드, 수정 불가 | 가입 시각 |
| `updatedAt` | 수정 감사 필드 | 마지막 엔티티 변경 시각 |

- `User.of(email, encodedPassword, username)`은 `deleted=false`인 사용자를 만든다.
- 신규 사용자의 `profileImageKey`는 별도로 설정하지 않으므로 null이다.
- `deleteUser()`는 row를 지우지 않고 `deleted=true`로 바꾼다.
- `update(username)`은 사용자명만 바꾼다.
- `updateProfileImageKey(key)`는 프로필 이미지 논리 key만 바꾼다.
- 생성/수정 시각은 `@EnableJpaAuditing`과 `AuditingEntityListener`가 관리한다.

### 2.2 상태 전이

```text
미가입
  -- POST /user 성공 --> ACTIVE(deleted=false, profileImageKey=null)

ACTIVE
  -- PATCH /me --> ACTIVE(username 변경 또는 동일 값 재설정)
  -- PUT /me/profile-image --> ACTIVE(profileImageKey 교체)
  -- DELETE /me --> DELETED(deleted=true)

DELETED
  -- 현재 복구 API 없음 --> DELETED 유지
```

- 삭제 사용자는 물리적으로 남기 때문에 기존 PK, 이메일, 사용자명, 프로필 key와 연관 데이터가 유지된다.
- `existsByEmail`과 `existsByUsername`은 `deleted`를 필터링하지 않는다. 따라서 삭제 사용자의 이메일과 사용자명도 다시 가입하거나 변경할 때 재사용할 수 없다.

## 3. API 공개 범위와 인증 경계

### 3.1 사용자 관련 HTTP API

| Method | Path | 인증 | 성공 응답 |
| --- | --- | --- | --- |
| `POST` | `/user` | 공개 | `200 OK`, `SignUpResponseDto` |
| `GET` | `/user/{userId}` | 공개 | `200 OK`, `OtherUserResponseDto` |
| `GET` | `/me` | AccessToken 필수 | `200 OK`, `UserResponseDto` |
| `PATCH` | `/me` | AccessToken 필수 | `200 OK`, body 없음 |
| `DELETE` | `/me` | AccessToken 필수 | `200 OK`, body 없음 |
| `PUT` | `/me/profile-image` | AccessToken 필수 | `200 OK`, body 없음 |
| `GET` | `/profile-images/{fileKey}/thumbnail` | 공개 | `200 OK`, `image/jpeg` binary |
| `GET` | `/profile-images/{fileKey}/image` | 공개 | `200 OK`, 원본 image binary |

- `GET /user/**`, `POST /user`, `GET /profile-images/**`는 `SecurityWhitelist`의 공개 filter chain에 들어가 JWT 필터를 거치지 않는다.
- `/me` 계열은 whitelist에 없으므로 인증 filter chain에서 AccessToken을 요구한다.
- 공개 API는 Authorization 헤더를 보내도 그 토큰으로 응답 범위를 넓히지 않는다.

### 3.2 보호 API의 인증 처리

1. `JwtSecurityFilter`가 `Authorization` 헤더를 읽는다.
2. 헤더가 없으면 `J005 / 401`, `Bearer ` 형식이 아니면 `J006 / 401`로 filter에서 요청을 끝낸다.
3. JWT의 서명, 만료, 지원 형식과 token type을 검사한다.
4. `type=access`가 아니면 `J001 / 401`이다.
5. claim의 `sub`를 `Long userId`, `sid`를 세션 식별자로 읽는다.
6. `AuthenticatedUser(userId, sid)`를 principal로 넣는다.
7. 컨트롤러는 principal의 userId를 서비스에 전달한다. 본인 API에서 요청 path/body로 userId를 받지 않는다.

- Security 계층은 현재 별도 role/authority 없이 인증 여부만 판정한다.
- AccessToken은 Redis에 저장하지 않으므로 필터는 DB 사용자 존재/삭제 상태를 알지 못한다.
- 각 보호 유스케이스는 서비스에서 사용자를 다시 조회하고 `deleted=false`를 검증해야 한다.

## 4. 요청 검증, 응답 DTO, 오류 형식

### 4.1 가입 요청 `SignUpRequestDto`

| 필드 | 서버 검증 |
| --- | --- |
| `email` | 이메일 형식, 필수/공백 불가, 최대 254자 |
| `password` | 필수/공백 불가 |
| `username` | 필수/공백 불가, 최대 50자 |

- 비밀번호에는 현재 최소/최대 길이, 문자 조합, 유출 비밀번호 검사 같은 추가 Bean Validation이 없다.
- 서버는 email/username의 trim, 소문자화, Unicode 정규화를 하지 않는다.
- 여러 필드가 동시에 잘못되면 전역 처리기가 binding result의 첫 번째 field error 메시지만 반환한다.

### 4.2 사용자명 수정 요청 `UserUpdateRequestDto`

| 필드 | 서버 검증 |
| --- | --- |
| `username` | 필수/공백 불가, 최대 50자 |

- username 이외 필드를 보내도 해당 DTO에는 반영할 필드가 없다.
- 이메일·비밀번호 변경 API는 현재 없다.

### 4.3 성공 응답 필드

`POST /user`

```json
{
  "userId": 1,
  "username": "user1",
  "createdAt": "2026-08-18 10:00:00",
  "updatedAt": "2026-08-18 10:00:00"
}
```

`GET /user/{userId}`

```json
{
  "userId": 1,
  "username": "user1",
  "createdAt": "2026-08-18 10:00:00",
  "updatedAt": "2026-08-18 10:00:00"
}
```

`GET /me`

```json
{
  "userId": 1,
  "email": "user@example.com",
  "username": "user1",
  "profileImageKey": "user:1:uuid-or-null",
  "createdAt": "2026-08-18 10:00:00",
  "updatedAt": "2026-08-18 10:00:00"
}
```

- 세 DTO 모두 password와 `deleted`를 노출하지 않는다.
- 공개 타 사용자 응답은 email뿐 아니라 현재 `profileImageKey`도 포함하지 않는다.
- 날짜는 `LocalDateTime`을 `yyyy-MM-dd HH:mm:ss`로 직렬화하며 UTC offset/timezone 정보는 없다.

### 4.4 오류 응답

`GlobalExceptionHandler`가 서비스 오류와 Bean Validation 오류를 다음 구조로 반환한다.

```json
{
  "code": "U003",
  "status": 404,
  "message": "존재하지 않는 유저입니다.",
  "timestamp": "2026-08-18 10:00:00"
}
```

- `ErrorException`은 해당 `ErrorCode`의 상태·코드·메시지를 사용한다.
- Bean Validation은 `C001 / 400`을 사용하되 구체적인 필드 메시지를 넣는다.
- 별도로 처리하지 않은 예외는 `S001 / 500`으로 변환된다.

## 5. `UserRepository`와 실제 조회 의미

사용자 도메인 repository는 Spring Data JPA를 사용하며 사용자 전용 MyBatis mapper는 없다.

| 메서드 | 의미/사용처 |
| --- | --- |
| `findById(userId)` | PK 조회. 각 사용자 서비스의 active 검사 기반 |
| `findByEmail(email)` | 로그인 사용자 조회 |
| `findByUsername(username)` | 친구 추가 대상 조회 |
| `existsByEmail(email)` | 가입 이메일 중복 사전 검사 |
| `existsByUsername(username)` | 가입·사용자명 수정 중복 사전 검사 |
| `save(user)` | 신규 사용자 INSERT |
| `findAllInteractingUserIds(userId)` | 사용자명/프로필 변경 이벤트 수신자 계산 |

### 5.1 공통 active 사용자 조회

`UserService.findActiveUserById`는 다음 순서를 모든 본인/공개 사용자 유스케이스에서 재사용한다.

1. `findById` 결과가 없으면 `U003`.
2. row가 있어도 `deleted=true`면 동일한 `U003`.
3. active `User`만 호출자에게 반환.

외부 응답만으로 "존재하지 않음"과 "삭제됨"을 구분할 수 없다.

### 5.2 메타데이터 이벤트 수신자 쿼리

`findAllInteractingUserIds(userId)` JPQL은 다음 집합을 반환한다.

```text
사용자 userId가 ChatRoomUser row를 가진 모든 채팅방
  -> 그 채팅방의 다른 ChatRoomUser 중
     receiver.user.deleted = false
     AND receiver.chatRoomUserStatus = ACTIVE
     AND receiver.user.userId != userId
  -> receiver.user.userId를 DISTINCT 반환
```

중요한 세부 조건은 다음과 같다.

- 이벤트를 일으킨 사용자의 `ChatRoomUser` 상태에는 조건이 없다. 즉 사용자가 `LEFT`인 과거 채팅방도 subquery에 포함된다.
- 수신자는 현재 그 방에서 `ACTIVE`이고 삭제되지 않아야 한다.
- 동일 사용자가 여러 방에서 겹쳐도 `distinct`로 한 번만 반환한다.
- 자기 자신은 명시적으로 제외된다. 같은 사용자의 다른 WebSocket 세션도 이 이벤트를 받지 않는다.
- 친구 관계 유무는 조건에 없다. 공유 채팅방 기반 수신자 목록이다.
- 정렬 조건은 없으며 이벤트 의미에도 순서가 필요하지 않다.

## 6. `POST /user` — 회원가입

### 6.1 서버 처리 순서

`UserService.signUpUser`는 쓰기 트랜잭션이다.

1. 컨트롤러의 `@Valid`가 email/password/username을 검증한다.
2. `existsByEmail(request.email)`을 먼저 실행한다.
3. 이미 존재하면 즉시 `U001 / 409`를 던지고 username 검사와 인코딩은 하지 않는다.
4. `existsByUsername(request.username)`을 실행한다.
5. 이미 존재하면 `U002 / 409`를 던지고 비밀번호 인코딩과 저장은 하지 않는다.
6. `BCryptPasswordEncoder.encode(rawPassword)`로 비밀번호를 단방향 인코딩한다.
7. `User.of(email, encodedPassword, username)`으로 `deleted=false` 엔티티를 만든다.
8. `UserRepository.save`로 INSERT한다.
9. 저장된 엔티티의 `userId`, `username`, `createdAt`, `updatedAt`만 응답 DTO에 넣는다.
10. `200 OK`로 반환한다. 가입 성공이 자동 로그인이나 토큰 발급을 의미하지는 않는다.

### 6.2 데이터 및 동시성 경계

- JPA 매핑은 email과 username에 각각 unique 제약을 선언한다.
- `exists` 사전 검사와 INSERT는 한 SQL 문장이 아니므로 동시 가입 경쟁에서는 둘 다 사전 검사를 통과할 수 있다.
- DB unique 제약이 최종 중복을 막지만, 그 persistence 예외를 `U001/U002`로 분류하는 전용 처리는 현재 없다. 경쟁 조건은 `S001 / 500`이 될 수 있다.
- 중복 검사는 삭제 row도 포함하므로 삭제 사용자의 식별자는 재사용할 수 없다.

## 7. `GET /user/{userId}` — 공개 타 사용자 조회

`UserService.findOtherUser`는 read-only 트랜잭션이다.

1. path의 `userId`로 `findActiveUserById`를 호출한다.
2. 없거나 삭제된 사용자는 `U003 / 404`다.
3. active 사용자를 `OtherUserResponseDto`로 변환한다.
4. `userId`, `username`, `createdAt`, `updatedAt`만 `200 OK`로 반환한다.

- 인증 없이 호출 가능하다.
- email, password, `deleted`, `profileImageKey`는 반환하지 않는다.
- 요청자가 누구인지에 따른 추가 접근 제어는 없다.

## 8. `GET /me` — 본인 조회

`UserService.findUser`는 read-only 트랜잭션이다.

1. AccessToken 인증 결과의 userId를 받는다.
2. `findActiveUserById`로 DB row와 soft-delete 상태를 확인한다.
3. active 사용자를 `UserResponseDto`로 변환한다.
4. `userId`, `email`, `username`, `profileImageKey`, 생성/수정 시각을 반환한다.

- token 자체가 유효해도 DB row가 없거나 삭제 상태면 `U003 / 404`다.
- 본인 상태를 다시 동기화하고, 서버가 생성한 최신 프로필 key와 `updatedAt`을 받는 기준 API다.

## 9. `PATCH /me` — 사용자명 수정

### 9.1 서버 처리 순서

`UserService.updateUser`는 쓰기 트랜잭션이다.

1. AccessToken principal의 `loginUserId`로 active 사용자를 조회한다.
2. request의 `newUsername`과 현재 `User.username`을 `String.equals`로 비교한다.
3. 값이 다를 때만 `existsByUsername(newUsername)`을 실행한다.
4. 다른 사용자가 이미 쓰는 이름이면 `U002 / 409`를 던진다.
5. `User.update(newUsername)`을 호출한다. 별도 repository `save`는 호출하지 않고 JPA dirty checking으로 반영한다.
6. `findAllInteractingUserIds(loginUserId)`로 실시간 이벤트 수신자 ID 목록을 계산한다.
7. `USERNAME_UPDATED` 이벤트를 만든다.
8. 서비스 메서드가 정상 반환되면 DB 트랜잭션이 commit된다.
9. 컨트롤러가 반환된 이벤트를 Redis `user:metadata` 채널에 publish한다.
10. publish까지 성공하면 `200 OK`, body 없음으로 응답한다.

### 9.2 동일한 사용자명을 보낸 경우

- DB 중복 조회는 생략한다.
- 그래도 `User.update` 호출, 이벤트 수신자 조회, `USERNAME_UPDATED` 이벤트 생성과 publish는 수행한다.
- 응답은 변경된 경우와 동일하게 빈 `200 OK`다.

### 9.3 이벤트 객체

서비스가 만드는 내부 이벤트는 publish 전 다음 값을 갖는다.

```json
{
  "userMetadataEventType": "USERNAME_UPDATED",
  "userId": 1,
  "username": "newUsername",
  "userProfileImageKey": null,
  "eventUserIds": [2, 3, 4]
}
```

- `eventUserIds`는 Redis subscriber가 라우팅에 사용한 뒤 null로 지우므로 최종 STOMP 수신 body에는 대상 사용자 목록이 노출되지 않는다.
- 자기 자신은 수신자 쿼리에서 빠진다.
- 친구이지만 공유 채팅방이 없는 사용자도 수신자에서 빠질 수 있다.

### 9.4 트랜잭션과 publish의 경계

- `@Transactional`은 service 메서드에 있고 Redis publish는 service 반환 후 controller에서 실행된다.
- 따라서 DB commit과 Redis publish는 하나의 원자적 트랜잭션이 아니다.
- DB commit 뒤 Redis 직렬화/발행이 실패하면 HTTP는 `S001 / 500`이 될 수 있지만 사용자명은 이미 변경됐을 수 있다.
- 이벤트는 outbox에 저장하지 않으며 Redis Pub/Sub도 재전송을 보장하지 않는다.

## 10. `DELETE /me` — 사용자 소프트 삭제

### 10.1 서버 처리 순서

`UserService.deleteUser`는 쓰기 트랜잭션이다.

1. AccessToken principal의 userId로 active 사용자를 조회한다.
2. row가 없거나 이미 삭제됐으면 `U003 / 404`다.
3. `User.deleteUser()`로 persistence context의 `deleted`를 `true`로 바꾼다.
4. `RedisTokenStore.deleteAllRefreshTokens(userId)`를 호출한다.
5. Redis의 `USER_SESSIONS:{userId}` Sorted Set에서 모든 sid를 조회한다.
6. 각 sid를 `RT:{sid}` key로 바꿔 RefreshToken 값들을 삭제한다.
7. `USER_SESSIONS:{userId}` key 자체를 삭제한다.
8. 메서드 종료 시 JPA dirty checking으로 사용자 soft-delete가 반영된다.
9. controller가 `200 OK`, body 없음으로 반환한다.

### 10.2 삭제 뒤 서버 동작

- 로그인은 email로 row를 찾더라도 `deleted=true`를 보고 `U003`으로 거절한다.
- 사용자 세션 인덱스에 등록된 RefreshToken 재발급은 Redis key 제거로 실패한다. 인덱스에 없던 고아 `RT:{sid}`가 남아 Redis 비교를 통과하더라도 DB의 `deleted=true` 검사에서 `U003`으로 실패한다.
- 사용자/친구/채팅 등 active 검사를 하는 보호 서비스는 기존 AccessToken으로 들어온 요청을 `U003` 등으로 거절한다.
- 친구 목록과 active friend 쿼리에서는 삭제 사용자가 제외된다.
- 사용자 row, 친구 관계 row, 채팅방 참여/메시지, 프로필 파일은 이 메서드에서 물리 삭제하지 않는다.
- 삭제 사용자가 GROUP 방장이었어도 `ChatRoomUser`의 `ACTIVE/OWNER` 상태를 변경하거나 다른 멤버에게 방장을 양도하지 않는다. 남은 멤버가 모두 `MEMBER`이면 공통 방 이름 변경은 `CR019`로 막히고 자동 복구 API도 없다.
- 사용자 삭제 이벤트나 WebSocket 메타데이터 이벤트는 발행하지 않는다.
- 삭제 사용자를 되살리는 API는 없다.

### 10.3 남아 있는 토큰과 쿠키

- AccessToken은 Redis에 없으므로 만료 전까지 JWT 서명 검증 자체는 성공할 수 있다. 이후 서비스의 active 사용자 검사가 실제 기능 수행을 막는다.
- MediaToken도 stateless JWT이며 이 삭제 메서드가 별도 blacklist를 만들지 않는다. 채팅 파일 조회 서비스는 token의 userId로 active 사용자를 다시 확인한다.
- `DELETE /me` 응답은 `refreshToken`/`mediaToken` 쿠키를 만료시키는 `Set-Cookie`를 내려주지 않는다.
- 브라우저에 HttpOnly 쿠키 문자열이 남을 수 있지만 제거된 Redis RefreshToken은 재발급에 쓸 수 없다.

### 10.4 DB와 Redis의 원자성

- DB 트랜잭션 안에서 Redis 삭제를 호출하지만 JDBC 변경과 Redis 명령이 하나의 분산 트랜잭션으로 묶이지는 않는다.
- Redis 작업 도중 실패하면 DB 트랜잭션은 예외로 rollback될 수 있지만 이미 실행된 일부 Redis 삭제까지 자동 복구된다는 보장은 없다.
- 반대로 Redis 삭제 후 DB commit이 실패하면 세션은 제거됐지만 사용자 row는 active로 남을 수 있다.

## 11. `PUT /me/profile-image` — 프로필 이미지 교체

이 엔드포인트는 `StoredFileController/Service`에 있지만 `User.profileImageKey`를 직접 변경한다.

### 11.1 요청 계약

- `multipart/form-data`를 사용한다.
- part 이름은 `userProfileImage`다.
- part가 없거나 `MultipartFile.isEmpty()`이면 `PROFILE_FILE_REQUIRED`가 발생한다.
- 실제 `ErrorCode` 값은 현재 `SF003 / 400`, 메시지는 `비어 있지 않은 파일을 1개 선택해야 합니다.`다.
- 프로필 전용 크기 제한은 서비스에 없고, 공통 multipart 설정의 파일당 3GB·요청당 3100MB 제한만 적용된다.
- 파일 확장자나 요청 Content-Type만 믿지 않고 `ImageIO`의 image reader와 reader가 보고한 format을 검사한다.
- reader가 없거나 format이 `jpeg`, `png`, `gif`, `webp` 밖이면 `SF001 / 400`이다. reader가 존재하더라도 실제 첫 프레임 디코딩 중 `IOException`이 나면 전용 파일 오류가 아니라 `S001 / 500`으로 변환된다.

### 11.2 서버 처리 순서

`StoredFileService.saveUserProfile`은 쓰기 트랜잭션이다.

1. AccessToken userId로 사용자를 조회하고, 없거나 삭제됐으면 `U003 / 404`를 던진다.
2. multipart part 존재와 비어 있지 않음을 확인한다.
3. 기존 `profileImageKey`를 이후 정리를 위해 보관한다.
4. `user:{userId}:{UUID}` 형식의 새 key를 만든다.
5. `User.updateProfileImageKey(newKey)`로 persistence context의 User를 갱신한다.
6. 파일 stream에서 ImageIO reader를 찾고 format을 판별한다.
7. 애니메이션 GIF/WebP라도 첫 프레임을 thumbnail 원본으로 읽는다.
8. 원본 저장 파일명은 별도 UUID로 만들고, JPEG 확장자는 `.jpg`, 나머지는 판별 format 확장자를 사용한다.
9. 원본 파일을 `${file_root_path}` 아래에 저장한다.
10. 첫 프레임으로 최대 320x320, 종횡비 유지, JPEG quality 0.9의 thumbnail을 만든다.
11. 동일한 새 key로 `StoredFile` row 두 개를 저장한다.
12. ORIGINAL row는 실제 원본 content type/크기, THUMBNAIL row는 `image/jpeg`/생성된 thumbnail 크기를 가진다.
13. 둘 다 `fileOrder=1`, `fileCategory=IMAGE`다.
14. `findAllInteractingUserIds(userId)`로 이벤트 수신자를 먼저 계산한다.
15. 이전 key가 있으면 그 key의 모든 `StoredFile` row를 조회한다.
16. 각 이전 실제 파일을 `deleteIfExists`로 지운다. 삭제 실패는 warn log만 남기고 교체를 계속한다.
17. 이전 `StoredFile` row들을 삭제한다.
18. `USER_PROFILE_IMAGE_UPDATE` 이벤트를 반환한다.
19. service 트랜잭션 commit 후 controller가 Redis `user:metadata`에 이벤트를 publish한다.
20. publish까지 성공하면 `200 OK`, body 없음이다.

### 11.3 파일 오류 정리 동작

- 새 원본 또는 thumbnail 쓰기 과정에서 `IOException`이 발생하면 새로 만든 두 path에 대해 `deleteIfExists`를 시도한 후 예외를 다시 던진다.
- image reader 생성·첫 프레임 디코딩은 이 물리 파일 보상 블록보다 앞에 있다. 이 단계의 `IOException`은 `RuntimeException`으로 바뀌지만 아직 새 물리 파일을 만들기 전이므로 삭제 대상은 없다.
- 이전 파일의 물리 삭제 실패는 새 프로필 반영을 막지 않고 로그만 남긴다. DB의 이전 `StoredFile` row 삭제는 계속 진행한다.
- 로컬 파일시스템 작업은 DB 트랜잭션 자원이 아니다. DB rollback이 실제 파일 생성/삭제를 자동 rollback하지 않는다.
- 새 파일 저장 뒤 repository/event 수신자 조회 같은 후속 단계에서 실패하는 경우를 모두 보상하는 범용 정리 로직은 현재 없다.

### 11.4 프로필 변경 이벤트

```json
{
  "userMetadataEventType": "USER_PROFILE_IMAGE_UPDATE",
  "userId": 1,
  "username": null,
  "userProfileImageKey": "user:1:new-uuid",
  "eventUserIds": [2, 3, 4]
}
```

- 수신자 규칙과 publish 경계는 사용자명 수정과 같다.
- 자기 자신은 수신자가 아니며 PUT 응답에도 새 key가 없다. 호출 클라이언트가 정확한 새 key를 얻는 기준은 성공 뒤 `GET /me` 재조회다.

## 12. 프로필 이미지 공개 조회

### 12.1 Thumbnail — `GET /profile-images/{fileKey}/thumbnail`

1. `fileKey + THUMBNAIL`로 `StoredFile` row를 조회한다.
2. row가 없으면 `SF002 / 404`다.
3. `${file_root_path}/{storedFileName}`이 실제 regular file인지 확인한다.
4. 실제 파일이 없어도 `SF002 / 404`다.
5. `image/jpeg`로 반환한다.
6. `Cache-Control: public, max-age=365일, immutable`을 설정한다.

### 12.2 Original — `GET /profile-images/{fileKey}/image`

1. `fileKey + ORIGINAL`로 `StoredFile` row를 조회한다.
2. 실제 파일 존재까지 확인한다.
3. 저장 row의 content type으로 원본을 반환한다.
4. thumbnail과 동일하게 365일 public immutable cache를 설정한다.

### 12.3 접근 범위

- 두 조회 API 모두 인증 없이 공개되어 있고 사용자 active 상태를 다시 확인하지 않는다.
- key를 알고 있고 `StoredFile` row와 실제 파일이 남아 있으면 조회할 수 있다.
- 서비스는 `fileKey`가 `user:` prefix인지 또는 실제 사용자 프로필 용도인지 검사하지 않는다. 단일 파일 메시지의 `chat-message:{messageId}` key도 조건에 맞으면 공개 경로로 조회될 수 있어 보호 채팅 파일 권한 검사를 우회한다. 파일 용도 혼동과 응답 MIME·캐시 위험은 [FILEFLOW.md](./FILEFLOW.md)에 상세히 정리한다.
- 사용자 소프트 삭제는 프로필 key나 저장 파일을 지우지 않으므로 삭제 뒤에도 해당 URL이 계속 응답할 수 있다.
- 프로필 교체는 새 key를 만들고 이전 파일/row를 삭제하므로 최신 응답에서는 반드시 새 key 기반 URL을 사용해야 한다.

## 13. 사용자 메타데이터 실시간 전파

### 13.1 전체 파이프라인

```text
PATCH /me 또는 PUT /me/profile-image
  -> DB 변경 + 수신자 ID 계산(service transaction)
  -> transaction commit
  -> UserMetadataEvent JSON 직렬화
  -> Redis channel user:metadata publish
  -> 각 서버 인스턴스 RedisSubscriber 수신
  -> eventUserIds 보관 후 payload에서는 null로 제거
  -> 각 userId에 convertAndSendToUser(..., /queue/users/metadata)
  -> 클라이언트 /user/queue/users/metadata 구독으로 수신
```

- Redis listener container는 `user:metadata` 채널을 구독한다.
- STOMP는 `/user`를 user destination prefix로, `/queue`를 simple broker prefix로 사용한다.
- WebSocket CONNECT 때 AccessToken의 userId로 `StompPrincipal.getName()`을 구성하므로 `convertAndSendToUser(String.valueOf(userId), ...)`와 연결된다.
- 이벤트는 DB에 저장하지 않으며 sequence/version도 없다.
- Redis Pub/Sub와 simple broker는 offline client를 위한 durable queue나 replay를 제공하지 않는다.
- subscriber 처리 예외는 로그를 남기지만 원 HTTP 요청은 이미 끝났거나 다른 실행 경계에 있다.

### 13.2 클라이언트에 전달되는 필드

| 이벤트 종류 | `username` | `userProfileImageKey` | 의미 |
| --- | --- | --- | --- |
| `USERNAME_UPDATED` | 새 사용자명 | null | 표시명 교체 |
| `USER_PROFILE_IMAGE_UPDATE` | null | 새 key | 프로필 URL 교체 |

- 두 이벤트 모두 `userId`를 포함한다.
- routing용 `eventUserIds`는 subscriber가 null로 만든 뒤 전송한다.

## 14. 인증·친구·채팅 도메인과의 연결

### 14.1 인증

- 로그인은 `findByEmail` 후 `deleted=false`를 확인하고 BCrypt `matches`로 비밀번호를 검증한다.
- RefreshToken 재발급도 Redis token 일치 확인 뒤 사용자 row와 삭제 상태를 다시 확인한다.
- 사용자 삭제는 `USER_SESSIONS:{userId}` 인덱스에 등록된 Redis RefreshToken 세션을 제거한다. 인덱스에 없는 고아 key는 직접 열거하지 못하지만 삭제 사용자 검사가 재발급을 차단한다.
- AccessToken/MediaToken은 stateless이므로 각 실제 서비스의 active 사용자 검사가 최종 방어선이다.

### 14.2 친구

- 친구 추가는 현재 username으로 대상을 찾는다. 사용자명 변경 직후에는 새 이름으로만 검색된다.
- 친구 관계는 User FK를 가리키므로 username/profile key 변경 뒤에도 관계는 유지되며 다음 목록 조회에 최신 값이 보인다.
- 사용자 삭제 뒤 관계 row는 남지만 active friend 쿼리가 대상을 숨긴다.

### 14.3 채팅

- `findAllInteractingUserIds`는 변경 사용자가 상태와 무관하게 `ChatRoomUser` 행을 가진 모든 방에서, 현재 `ACTIVE`이고 삭제되지 않은 다른 사용자에게 사용자명/프로필 변경을 전파한다.
- 채팅 메시지와 방 목록/참여자 DTO도 `User.username`과 `profileImageKey`를 사용하므로 이벤트 또는 재조회로 표시 정보를 갱신해야 한다.
- 사용자 삭제 시 채팅 데이터 자체는 삭제하지 않는다. 각 채팅 쿼리/서비스가 삭제 사용자 처리 정책을 별도로 적용한다.

## 15. 프론트엔드 관리 책임

이 절은 서버 처리와 구분한 클라이언트 책임이다.

### 15.1 가입과 로그인 상태

- 가입 성공 응답에는 토큰이 없다. 성공 뒤 별도 `POST /login`을 수행해야 인증 상태가 된다.
- email/username은 서버가 자동 trim 또는 대소문자 정규화하지 않으므로, UI에서 임의로 다른 값을 보여주면서 원문을 전송하지 않도록 한다.
- 프론트 validation은 사용자 경험 개선용이며 서버의 `C001/U001/U002` 응답을 최종 판단으로 사용한다.
- 서버는 password에 `NotBlank` 외 복잡도 제한을 두지 않지만, 프론트 제한을 서버 보안 규칙으로 오해해서는 안 된다.

### 15.2 본인 상태의 기준 데이터

- 로그인/새로고침/재연결 뒤 `GET /me`로 `userId`, email, 최신 username, `profileImageKey`를 동기화한다.
- `PATCH /me`는 body가 없으므로 요청값을 낙관적으로 적용하더라도 실패 시 되돌리고, 정확한 `updatedAt`이 필요하면 `GET /me`를 다시 호출한다.
- `PUT /me/profile-image`는 서버가 key를 생성하고 body로 반환하지 않으며 자기 자신에게 이벤트도 보내지 않는다. 성공 뒤 반드시 `GET /me`로 새 key를 가져와야 한다.

### 15.3 AccessToken과 삭제 처리

- 보호 요청에는 `Authorization: Bearer {accessToken}`을 보낸다.
- AccessToken 만료 `401`과 active 사용자 실패 `U003 / 404`를 구분한다.
- 계정 삭제 성공 즉시 클라이언트가 보관하는 AccessToken과 사용자 캐시를 폐기하고 로그아웃 상태로 전환한다.
- `DELETE /me`는 HttpOnly refresh/media 쿠키를 직접 만료시키지 않는다. 인덱스에 등록됐던 refresh 세션은 Redis에서 제거되고, 고아 key가 남은 경우도 삭제 사용자 DB 검사에서 재발급이 거절된다는 서버 특성을 고려한다.
- 삭제 요청의 응답이 네트워크 오류나 `500`이면 DB/Redis가 부분 성공했을 가능성을 단정하지 말고 `GET /me` 또는 로그인/재발급 결과로 상태를 재확인한다.

### 15.4 프로필 이미지 URL과 캐시

- `profileImageKey=null`이면 기본 이미지를 표시한다.
- key를 로컬 파일 경로나 확장자로 해석하지 말고 path parameter용 불투명 값으로 다룬다.
- 목록용 이미지는 `/profile-images/{key}/thumbnail`, 확대용은 `/profile-images/{key}/image`를 사용한다.
- 서버가 1년 immutable cache를 주므로 프로필 변경 시 같은 URL을 강제 refresh하기보다 새 key의 새 URL로 교체한다.
- 실시간 `USER_PROFILE_IMAGE_UPDATE`를 받으면 해당 `userId`의 모든 화면 캐시 key를 새 값으로 바꾼다.

### 15.5 실시간 사용자 정보

- STOMP 개인 destination `/user/queue/users/metadata`를 구독한다.
- `USERNAME_UPDATED`와 `USER_PROFILE_IMAGE_UPDATE`를 type으로 분기하고 null인 비대상 필드를 덮어쓰지 않는다.
- 이벤트는 유실 가능하고 자기 자신의 다른 세션에는 오지 않으므로, 화면 진입·WebSocket 재연결·백그라운드 복귀 때 HTTP 조회를 source of truth로 사용한다.
- 이벤트 수신 뒤 사용자명 정렬을 사용하는 친구/참여자 목록은 필요하면 재정렬한다.
- 사용자 삭제 이벤트는 없으므로 관련 API의 `U003`, 목록 재조회, 채팅 정책 응답을 통해 삭제 상태를 반영한다.

### 15.6 공개 사용자 조회

- `GET /user/{id}`는 공개 정보만 주며 프로필 key가 없다. 이 응답만으로 프로필 이미지 URL을 만들 수 있다고 가정하지 않는다.
- 존재하지 않는 사용자와 삭제 사용자는 모두 같은 `U003`이므로 UI 문구도 서버가 구분하지 않는다는 점을 반영한다.

## 16. 주요 오류 코드

| 코드 | HTTP | 발생 지점 |
| --- | --- | --- |
| `C001` | 400 | 가입/사용자명 request Bean Validation 실패 |
| `U001` | 409 | 가입 email 중복 사전 검사 |
| `U002` | 409 | 가입 또는 수정 username 중복 사전 검사 |
| `U003` | 404 | 사용자 없음 또는 soft-deleted |
| `J001` | 401 | 잘못된 서명/형식의 JWT 또는 access가 아닌 token type |
| `J002` | 401 | 만료된 AccessToken |
| `J003` | 401 | JWT parser가 지원하지 않는 token 형식 |
| `J004` | 401 | `Bearer ` 뒤 token 값이 비어 있는 경우 |
| `J005` | 401 | Authorization 헤더 누락 |
| `J006` | 401 | Authorization 헤더가 `Bearer `로 시작하지 않음 |
| `SF001` | 400 | ImageReader 없음 또는 reader가 보고한 프로필 이미지 format이 허용 목록 밖 |
| `SF002` | 404 | 프로필 파일 row 또는 실제 파일 없음 |
| `SF003` | 400 | 현재 코드상 프로필 multipart 누락/빈 파일 |
| `S001` | 500 | 별도 매핑되지 않은 persistence/Redis/파일 처리 예외 |

- `U005 FORBIDDEN_USER_ACCESS`는 `ErrorCode`에 선언되어 있지만 현재 main 코드에서 발생시키는 곳이 없다.

### 코드와 Swagger 설명의 현재 불일치

- `PUT /me/profile-image` Swagger 설명은 빈 프로필 파일 오류를 `SF006`이라고 적고 있다.
- 실제 `ErrorCode.PROFILE_FILE_REQUIRED`의 code는 `SF003`이며 `CHAT_FILE_REQUIRED`와 code가 중복된다.
- 클라이언트와 이 문서는 실행 코드의 실제 값인 `SF003`을 기준으로 한다.

## 17. 트랜잭션·일관성 요약

| 흐름 | DB 트랜잭션 | DB 밖 side effect | 원자성 주의 |
| --- | --- | --- | --- |
| 가입 | 쓰기 | BCrypt 계산 | unique 경쟁 예외 매핑 없음 |
| 공개/본인 조회 | read-only | 없음 | 조회 시점 snapshot |
| 사용자명 수정 | 쓰기 | commit 후 Redis publish | DB 성공/이벤트 실패 가능 |
| 사용자 삭제 | 쓰기 | 트랜잭션 도중 Redis token 삭제 | JDBC와 Redis 비원자적 |
| 프로필 변경 | 쓰기 | 로컬 파일 생성·삭제, commit 후 Redis publish | DB/파일/Redis 비원자적 |

- 사용자명과 프로필 이벤트에는 outbox, retry, idempotency key, version이 없다.
- 가입/사용자명 중복은 애플리케이션 사전 검사와 DB unique 제약을 함께 사용하지만 경쟁 예외의 도메인 코드 변환은 없다.
- 프로필 파일은 key를 매번 바꾸므로 HTTP cache busting은 되지만 파일시스템과 DB rollback은 자동 연동되지 않는다.

## 18. 현재 자동화 테스트가 확인하는 범위

### 18.1 `UserServiceTest`

- 가입 성공 시 email/password/username/deleted 값과 BCrypt 결과 저장을 확인한다.
- email 중복이면 username 검사·인코딩·저장을 중단하고, username 중복이면 인코딩·저장을 중단하는지 확인한다.
- 공개/본인 조회 성공 DTO와 존재하지 않음/삭제 사용자 `U003`을 확인한다.
- 다른 사용자명 수정 시 중복 검사, 엔티티 변경, interacting user 조회와 `USERNAME_UPDATED` 이벤트 필드를 확인한다.
- 동일 사용자명 수정 시 중복 검사는 생략하지만 이벤트는 만드는 현재 동작을 확인한다.
- 수정 실패 시 수신자 조회를 하지 않는지 확인한다.
- 삭제 성공 시 `deleted=true`와 `deleteAllRefreshTokens` 호출을 확인하고, 실패 시 Redis를 호출하지 않는지 확인한다.

### 18.2 `UserControllerTest`

- 가입 필드 validation 메시지, 성공 JSON 날짜 형식, `U001/U002` HTTP 매핑을 확인한다.
- 공개 조회와 본인 조회 JSON, `U003` 매핑을 확인한다.
- 사용자명 수정 성공 시 service가 반환한 동일 이벤트 객체를 `RedisPublisher`에 전달하는지 확인한다.
- validation/서비스 실패 시 Redis publish를 하지 않는지 확인한다.
- 삭제 성공/실패 HTTP 동작을 확인한다.
- 이 테스트는 `addFilters=false`로 Security filter를 끄고 principal을 직접 넣는다.

### 18.3 보안 전용 테스트

- 보호 API의 Authorization 누락 `401`, 유효 token의 controller 도달, 공개 API의 JWT filter 미사용을 별도 테스트한다.
- `AccessTokenAuthenticator`가 Bearer 형식, access type, `sub/sid` 추출을 확인한다.
- `JwtSecurityFilter`가 principal을 `SecurityContext`에 저장하고 인증 오류 JSON을 직접 쓰는지 확인한다.

### 18.4 현재 테스트가 직접 보장하지 않는 영역

- 실제 MySQL unique 제약, JPA auditing, `findAllInteractingUserIds` JPQL 통합 동작
- 가입/사용자명 변경의 동시 요청 경쟁 조건
- DB commit과 Redis publish 실패 사이의 부분 성공 처리
- Redis subscriber에서 STOMP client까지 사용자 메타데이터 종단 간 전달
- 프로필 이미지 저장·교체·공개 조회 및 실패 보상 동작
- 사용자 삭제 뒤 친구/채팅/파일 데이터 보존과 노출 정책의 통합 검증

## 19. 현재 제공하지 않는 사용자 흐름

- 이메일 변경 및 이메일 인증
- 비밀번호 변경/초기화
- 계정 복구/삭제 취소
- 사용자 hard delete와 연관 데이터 정리
- 프로필 이미지 제거 후 기본 이미지로 되돌리는 전용 API
- 사용자 검색/목록/pagination
- 삭제 이벤트 및 동일 사용자 다중 세션 메타데이터 동기화

이 기능들은 현재 API와 모델에 없으므로 다른 계층에서 존재한다고 가정해서는 안 된다.
