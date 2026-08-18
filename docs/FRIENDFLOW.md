# FRIENDFLOW.md

## 1. 문서 목적과 코드 기준

- 친구 도메인의 현재 서버 동작, 영속성 규칙, 채팅 도메인과의 연결 지점을 코드 기준으로 설명한다.
- 주 진입점은 `UserFriendController`, `UserFriendService`, `UserFriendRepository`, `UserFriend`다.
- 친구 관계를 사용하는 채팅 흐름은 `ChatRoomService`와 `ChatRoomController`까지 함께 추적한다.
- 인증 공통 동작은 `SecurityConfig`, `JwtSecurityFilter`, `AccessTokenAuthenticator`, `AuthenticatedUser`를 기준으로 한다.
- 이 문서의 "active friend"는 단순히 `user_friend` row가 존재하는 상태가 아니라, 요청자가 `owner`이고 대상 `friend.deleted = false`인 관계를 뜻한다.
- AccessToken의 생성·재발급 규칙은 [AUTHFLOW.md](./AUTHFLOW.md), 채팅방 생성·초대 전체 흐름은 [CHATFLOW.md](./CHATFLOW.md), `profileImageKey`와 실제 이미지 조회 규칙은 [FILEFLOW.md](./FILEFLOW.md)를 함께 참고한다.

## 2. 핵심 모델과 불변식

### 2.1 단방향 관계

- `UserFriend.owner`는 친구를 추가한 사용자다.
- `UserFriend.friend`는 추가된 대상 사용자다.
- `owner -> friend`만 저장되므로 A가 B를 추가해도 B의 목록에 A가 자동으로 생기지 않는다.
- 친구 여부를 사용하는 모든 현재 쿼리는 요청 사용자가 `owner`인 방향만 인정한다.
- 역방향 관계가 필요하면 B도 별도로 A를 추가해야 한다.

### 2.2 `UserFriend` 영속 모델

| 필드 | 매핑/제약 | 의미 |
| --- | --- | --- |
| `userFriendId` | `IDENTITY` PK | 친구 관계 식별자 |
| `owner` | `ManyToOne(fetch = LAZY)`, `owner_id NOT NULL` | 관계를 소유한 사용자 |
| `friend` | `ManyToOne(fetch = LAZY)`, `friend_id NOT NULL` | 소유자가 추가한 사용자 |
| `createdAt` | 생성 감사 필드, 수정 불가 | 관계 생성 시각 |
| `updatedAt` | 수정 감사 필드 | 관계 수정 시각. 현재 관계 수정 API는 없다. |

- 테이블에는 `(owner_id, friend_id)` 조합의 `uk_user_friends_owner_friend` unique 제약이 선언되어 있다.
- 엔티티 자체에는 `deleted`, `accepted`, `pending`, `blocked` 같은 관계 상태가 없다.
- 현재는 친구 요청/수락 모델이 아니라 즉시 생성되는 연락처형 단방향 관계다.
- cascade나 orphan removal은 선언되어 있지 않다.
- 친구 삭제 API와 repository 삭제 유스케이스도 현재 제공하지 않는다.

### 2.3 삭제 사용자와 관계 row

- 사용자 삭제는 `User.deleted = true`인 소프트 삭제이므로 기존 `UserFriend` row는 남는다.
- 대상 사용자가 삭제되면 active friend 쿼리에서 제외된다.
- 소유자가 삭제되면 친구 API의 서비스 진입 시 `U003`으로 차단된다.
- 삭제 사용자를 복구하거나 남은 친구 row를 정리하는 흐름은 현재 없다.

## 3. API와 인증 경계

| Method | Path | 인증 | 성공 응답 |
| --- | --- | --- | --- |
| `POST` | `/friends` | AccessToken 필수 | `200 OK`, body 없음 |
| `GET` | `/friends` | AccessToken 필수 | `200 OK`, JSON 배열 |

- 두 경로는 `SecurityWhitelist`에 없으므로 인증용 filter chain을 탄다.
- 서버는 `Authorization: Bearer {accessToken}`을 읽고 JWT의 서명·만료·형식·`type=access`를 검증한다.
- 검증 성공 시 JWT의 `sub`를 `userId`, `sid` claim을 세션 식별자로 읽어 `AuthenticatedUser`를 만들고 `SecurityContext`에 넣는다.
- 컨트롤러는 `@AuthenticationPrincipal`에서 `AuthenticatedUser.userId`만 꺼내 서비스에 전달한다. 친구 API 요청 body로 소유자 ID를 받지 않는다.
- Security 계층은 현재 role/authority를 부여하지 않고, "인증됨"만 요구한다.
- JWT 필터는 토큰 자체만 검증하고 DB 사용자 상태는 확인하지 않는다. 따라서 서비스가 매 요청마다 로그인 사용자의 존재 및 `deleted=false`를 다시 확인한다.
- 인증 단계 실패는 `401` JSON 응답으로 filter에서 즉시 종료된다. 서비스/검증 예외는 `GlobalExceptionHandler`가 처리한다.

공통 오류 body는 다음 필드를 갖는다.

| 필드 | 의미 |
| --- | --- |
| `code` | 애플리케이션 오류 코드 |
| `status` | HTTP 상태 정수 |
| `message` | 서버 오류 메시지 또는 Bean Validation 메시지 |
| `timestamp` | `yyyy-MM-dd HH:mm:ss` 형식의 발생 시각 |

## 4. Repository 쿼리와 사용처

친구 영속성은 현재 전부 Spring Data JPA와 JPQL을 사용한다. 기존 문서에 있던 "친구 목록은 MyBatis Mapper로 조회한다"는 현재 코드와 다르다.

### 4.1 중복 관계 확인

`existsByOwner_UserIdAndFriend_UserId(ownerId, friendId)`

- 메서드 이름으로 생성되는 derived query다.
- `(owner_id, friend_id)` row 존재 여부만 확인한다.
- 대상 사용자의 `deleted` 여부는 이 쿼리가 보지 않는다. 친구 추가 서비스가 그 전에 owner와 friend를 각각 조회해 active 여부를 검사한다.
- `POST /friends`의 친절한 중복 오류 `F001`을 만들기 위한 선행 검사에 사용된다.

### 4.2 한 명의 active friend 여부 확인

`existsActiveFriend(ownerId, friendId)`

```text
UserFriend.owner.userId = ownerId
AND UserFriend.friend.userId = friendId
AND UserFriend.friend.deleted = false
```

- `count(uf) > 0` JPQL 결과를 boolean으로 반환한다.
- `POST /directChatRooms`에서 1대1 대화 상대가 요청자의 단방향 active friend인지 검증한다.
- 반대 방향 관계만 있으면 `false`다.

### 4.3 ID 목록에 해당하는 active friend 조회

`findActiveFriendsByIds(userId, friendIds)`

```text
owner.userId = userId
AND friend.userId IN friendIds
AND friend.deleted = false
```

- 조건을 만족하는 `User` 엔티티 목록을 반환한다.
- 입력 ID 순서나 결과 정렬을 보장하는 `ORDER BY`는 없다.
- 호출 서비스는 요청 ID에서 중복을 제거한 뒤, 결과 수와 요청한 고유 ID 수가 같은지 비교하여 "모든 대상이 active friend인가"를 판정한다.
- 그룹 채팅방 생성, 1대1→그룹 전환 초대, 그룹 채팅방 초대, 채팅방 참여자별 친구 여부 계산에 재사용된다.

### 4.4 전체 active friend 조회

`findActiveFriends(userId)`

```text
owner.userId = userId
AND friend.deleted = false
```

- `GET /friends`에서 사용한다.
- repository 쿼리에는 정렬이 없고, 서비스가 Java에서 `User.username`의 `String.compareTo` 기준 오름차순으로 정렬한다.

### 4.5 채팅방별 초대 가능 친구 조회

`findInvitableFriends(userId, chatRoomId)`

- 기본 집합은 요청자가 owner인 active friend다.
- 서브쿼리로 해당 채팅방의 `ChatRoomUser`를 확인한다.
- 그룹 채팅방에서는 `ACTIVE` 참여자를 제외하고, 과거에 나간 `LEFT` 친구는 복귀 대상으로 포함한다.
- 1대1 채팅방에서는 참여 상태와 관계없이 기존 1대1 참여자를 모두 제외한다. 쿼리 조건이 `status = ACTIVE OR roomType = DIRECT`이기 때문이다.
- repository 단계에는 정렬이 없고 `ChatRoomService`가 사용자명 오름차순으로 정렬한다.

## 5. `POST /friends` — 친구 추가

### 5.1 요청 계약

```json
{
  "username": "friendUsername"
}
```

- `username`은 `@NotBlank`이므로 null, 빈 문자열, 공백만 있는 문자열을 허용하지 않는다.
- 최대 길이는 50자다.
- 서버는 입력값을 trim/strip하거나 대소문자를 정규화하지 않는다. 검증을 통과한 문자열 그대로 `UserRepository.findByUsername`에 전달한다.
- Bean Validation 실패 시 서비스는 호출되지 않으며 `C001 / 400`과 첫 번째 필드 오류 메시지가 응답된다.

### 5.2 서버 처리 순서

`UserFriendService.createFriend` 전체는 하나의 쓰기 트랜잭션이다.

1. JWT principal의 `loginUserId`로 `UserRepository.findById`를 실행한다.
2. owner row가 없으면 `U003 / 404`를 던진다.
3. owner의 `deleted`가 `true`여도 외부에는 동일한 `U003 / 404`로 응답한다.
4. 요청 `username`으로 `UserRepository.findByUsername`을 실행한다.
5. 대상 row가 없거나 대상의 `deleted`가 `true`면 `U003 / 404`를 던진다.
6. owner ID와 friend ID가 같으면 `F002 / 400`을 던진다.
7. `existsByOwner_UserIdAndFriend_UserId(ownerId, friendId)`로 동일 방향 관계를 확인한다.
8. 이미 존재하면 `F001 / 409`를 던진다.
9. `UserFriend.of(owner, friend)`로 관계 엔티티를 만든다.
10. `UserFriendRepository.save`로 저장한다.
11. 트랜잭션 commit 후 컨트롤러가 `200 OK`와 빈 body를 반환한다.

성공해도 대상 사용자에게 알림, 수락 요청, Redis 이벤트, STOMP 이벤트는 발행하지 않는다.

### 5.3 상태 변화

```text
관계 없음
  -> user_friend(owner_id = 로그인 사용자, friend_id = 대상 사용자) 생성
  -> 동일 방향 친구 관계 존재
```

- 반대 방향 row는 생성하지 않는다.
- `User` 엔티티 자체는 변경하지 않는다.
- `createdAt`, `updatedAt`은 JPA auditing으로 채워진다.

### 5.4 실패와 중단 지점

| 조건 | 응답 | 이후 작업 |
| --- | --- | --- |
| username 누락/공백 | `C001 / 400` | 사용자 조회 없음 |
| username 51자 이상 | `C001 / 400` | 사용자 조회 없음 |
| owner 없음/삭제됨 | `U003 / 404` | 대상 조회 없음 |
| 대상 없음/삭제됨 | `U003 / 404` | 중복 검사/저장 없음 |
| 자기 자신 | `F002 / 400` | 중복 검사/저장 없음 |
| 동일 방향 관계 존재 | `F001 / 409` | 저장 없음 |

### 5.5 동시성 경계

- 서비스의 `exists` 후 `save`는 원자적인 한 문장이 아니므로 같은 관계 추가 요청이 동시에 들어오면 둘 다 선행 검사를 통과할 수 있다.
- 최종 데이터 중복은 DB unique 제약이 막는다.
- 그러나 unique 제약 위반을 `F001`로 변환하는 전용 예외 처리는 현재 없다. 경쟁 조건에서 발생한 persistence 예외는 전역 일반 예외 처리에 의해 `S001 / 500`이 될 수 있다.

## 6. `GET /friends` — 친구 목록 조회

### 6.1 서버 처리 순서

`UserFriendService.findFriendListByOwnerId`는 read-only 트랜잭션이다.

1. JWT principal의 owner ID로 `UserRepository.findById`를 실행한다.
2. owner가 없거나 삭제 상태이면 `U003 / 404`를 던진다.
3. `findActiveFriends(ownerId)`로 owner가 추가한 친구 중 `friend.deleted=false`인 `User`만 조회한다.
4. 결과를 사용자명 기준 오름차순으로 Java 메모리에서 정렬한다.
5. 각 `User`를 `FriendListResponseDto`로 변환한다.
6. 관계가 없으면 오류가 아니라 빈 배열 `[]`을 반환한다.

### 6.2 응답 형식

```json
[
  {
    "friendId": 2,
    "friendUsername": "friendUsername",
    "profileImageKey": "user:2:uuid-or-null"
  }
]
```

- `friendId`는 대상 `User.userId`다.
- `friendUsername`은 조회 시점의 현재 사용자명이다. 관계 생성 당시 이름을 별도로 복제해 저장하지 않는다.
- `profileImageKey`도 조회 시점의 `User.profileImageKey`이며 프로필 이미지가 없으면 null일 수 있다.
- 이메일, 비밀번호, 관계 생성 시각은 노출하지 않는다.

## 7. 채팅 도메인에서 친구 관계를 사용하는 흐름

### 7.1 1대1 채팅방 생성 — `POST /directChatRooms`

1. 요청 사용자의 존재 및 삭제 여부를 먼저 확인한다.
2. 자기 자신 ID를 대화 상대로 요청하면 채팅 오류로 차단한다.
3. `existsActiveFriend(requesterId, friendId)`가 `true`여야 한다.
4. 즉, 요청자가 상대를 추가한 방향의 관계가 있어야 하고 상대가 삭제되지 않아야 한다.
5. 조건을 만족하지 않으면 `CR004`가 발생하며, 친구 도메인의 `F001/F002`가 사용되지는 않는다.

### 7.2 그룹 채팅방 생성 — `POST /groupChatRooms`

1. 요청 `friendIds`를 `HashSet`으로 중복 제거한다.
2. 빈 목록과 자기 자신 포함 여부를 검사한다.
3. `findActiveFriendsByIds(requesterId, uniqueFriendIds)`를 호출한다.
4. 조회된 사용자 수가 고유 요청 ID 수와 정확히 같아야 한다.
5. 하나라도 존재하지 않거나, 삭제됐거나, 요청자의 친구가 아니면 전체 요청을 `CR004`로 실패시킨다.

### 7.3 1대1 채팅방을 그룹으로 전환하며 초대 — `POST /directChatRooms/{chatRoomId}/invites`

- 기존 1대1 참여자 ID는 요청 목록에서 제외하고 "신규 초대자"만 분리한다.
- 신규 초대자가 최소 한 명 있어야 한다.
- 신규 초대자 전원을 `findActiveFriendsByIds`로 검증한다.
- 기존 1대1 상대는 이미 참여자이므로 신규 친구 검증 대상이 아니다.
- 신규 대상 중 하나라도 active friend가 아니면 `CR005`로 전체 요청이 실패한다.

### 7.4 그룹 채팅방 초대 — `POST /groupChatRooms/{chatRoomId}/invites`

- 요청 ID 중복을 제거하고, 빈 목록과 자기 자신 포함을 먼저 차단한다.
- 요청자의 active 채팅방 참여, active 사용자 여부, 그룹 채팅방 여부를 확인한다.
- 모든 대상이 요청자의 active friend인지 `findActiveFriendsByIds`와 개수 비교로 확인한다.
- 기존 `LEFT` 참여자는 복귀시키고, 채팅방 row가 없는 친구는 신규 참여자로 추가한다.
- 이미 `ACTIVE`인 대상만 남는 경우 등 후속 채팅 정책은 `ChatRoomService`가 처리한다.

### 7.5 초대 가능 친구 목록 — `GET /chatRooms/{chatRoomId}/invitableFriends`

- 먼저 요청자가 해당 채팅방의 `ACTIVE` 참여자이고 삭제되지 않은 사용자인지 확인한다.
- `findInvitableFriends` 결과를 사용자명 오름차순으로 정렬한다.
- 응답은 `userId`, `username`, `profileImageKey`를 포함한다.
- 그룹 방에서는 현재 `LEFT`인 친구가 복귀 후보로 보일 수 있다.
- 1대1 방에서는 기존 두 참여자가 `LEFT`여도 목록에서 제외된다.

### 7.6 채팅방 참여자 목록의 `canAddFriend` — `GET /chatRooms/{chatRoomId}/members`

- 화면에 보일 참여자의 ID 전체를 `findActiveFriendsByIds(requesterId, visibleUserIds)`로 조회한다.
- 각 참여자에 대해 active friend ID 집합에 없고 동시에 참여자 ID가 요청자 본인 ID와 다를 때만 `canAddFriend=true`로 응답한다.
- 이미 active friend이거나 요청자 본인이면 `canAddFriend=false`다.
- 따라서 서버 응답 단계에서 자기 자신에게 친구 추가 버튼이 생기지 않도록 차단하며, 실제 `POST /friends`도 자기 자신 요청을 `F002`로 다시 거절한다.

## 8. 사용자 변경이 친구 화면에 미치는 영향

- 친구 관계는 `User` FK를 참조하므로 대상이 사용자명을 바꿔도 관계 row를 다시 만들 필요가 없다.
- 다음 `GET /friends`에서는 최신 `User.username`이 반환된다.
- 프로필 이미지가 바뀌면 다음 조회에서 최신 `profileImageKey`가 반환된다.
- 사용자명/프로필 이미지 실시간 이벤트의 수신자는 "친구"나 메시지 상호작용 기준이 아니다. 변경 사용자가 상태와 무관하게 참여 행을 가진 모든 방에서 현재 `ACTIVE`이고 삭제되지 않은 다른 사용자가 대상이다.
- 따라서 친구이지만 공유 채팅방이 없는 사용자는 친구 메타데이터 변경 이벤트를 받지 못할 수 있다.
- 사용자가 삭제되면 관계 row는 남지만 다음 친구 목록과 active friend 검증에서 제외된다.
- 사용자 삭제 자체를 친구에게 알리는 전용 이벤트는 현재 없다.

## 9. 프론트엔드 관리 책임

이 절은 서버 내부 동작과 분리한 클라이언트 책임이다.

### 9.1 인증과 재요청

- `/friends` 요청에는 메모리 등 클라이언트가 관리하는 AccessToken을 `Authorization: Bearer ...`로 붙인다.
- `401`은 친구 관계 오류가 아니라 AccessToken 헤더/형식/서명/만료 문제로 처리한다.
- 재발급 성공 후에는 새 AccessToken으로 원 요청을 제한적으로 재시도하고, 재발급 실패 시 로그인 상태를 종료한다.
- `U003 / 404`가 로그인 사용자 삭제 때문에 발생할 수도 있으므로 단순히 "검색 대상 없음"으로만 표시하지 않는다.

### 9.2 친구 추가 UI

- 서버가 식별에 사용하는 값은 현재 username이다. 표시명과 전송값을 혼동하지 않는다.
- 성공 body는 없으므로 성공 후 로컬 목록에 임의 객체를 만들기보다 `GET /friends`를 다시 조회하면 서버 정렬과 최신 프로필 키를 그대로 반영할 수 있다.
- 관계가 단방향임을 UI 문구와 상태 모델에 반영한다. 서버 성공을 상호 친구 성립으로 해석하지 않는다.
- 현재 삭제 API가 없으므로 서버 기능이 있는 것처럼 친구 삭제 상태를 영구 반영하지 않는다.
- `F001`, `F002`, `U003`, `C001`을 구분해 사용자에게 보여준다.

### 9.3 목록과 프로필 이미지

- 응답 배열 순서는 서버가 username 오름차순으로 만들어 주지만, 실시간 이벤트로 로컬 값을 바꾼 뒤에는 필요하면 동일 기준으로 재정렬한다.
- `profileImageKey=null`이면 기본 이미지를 사용한다.
- key는 파일명이 아니라 서버 조회용 불투명 식별자로 취급한다.
- 썸네일은 `/profile-images/{profileImageKey}/thumbnail`, 원본은 `/profile-images/{profileImageKey}/image`로 조회한다.
- 프로필 key가 바뀌면 URL도 교체한다. 이전 key의 파일/DB row는 프로필 교체 과정에서 삭제될 수 있다.

### 9.4 실시간 메타데이터 동기화

- STOMP 연결 후 개인 destination `/user/queue/users/metadata`를 구독하면, 변경 사용자가 참여 행을 가진 방의 현재 `ACTIVE`·미삭제 상대에 해당할 때 사용자명/프로필 변경 이벤트를 받을 수 있다.
- 이벤트는 Redis Pub/Sub와 인메모리 STOMP broker를 지나며 저장·재전송되지 않는다. 오프라인 또는 재연결 구간의 이벤트는 놓칠 수 있다.
- 친구 관계만 있고 공유 채팅방이 없으면 이벤트 대상이 아닐 수 있으므로, 화면 진입/재연결/복구 시 `GET /friends`를 기준 데이터로 다시 조회한다.
- `USERNAME_UPDATED`이면 `userId`가 같은 목록 항목의 이름을, `USER_PROFILE_IMAGE_UPDATE`이면 프로필 key를 갱신한다.

### 9.5 채팅 참여자 화면

- 서버가 계산한 `canAddFriend`가 `true`일 때만 친구 추가 동작을 노출한다. 서버는 이미 active friend인 참여자와 요청자 본인에 대해 `false`를 반환한다.
- 클라이언트가 캐시한 로그인 `userId`로도 본인 항목을 식별할 수 있지만, 별도의 반대 규칙으로 서버 값을 재계산하지 않는다.
- 버튼 클릭 뒤 `POST /friends`가 최종 권한/중복/자기 자신 검증을 수행하므로 응답을 최종 상태로 사용한다.
- 초대 UI는 가능하면 `/chatRooms/{chatRoomId}/invitableFriends` 결과를 사용한다. 전체 친구 목록만으로 현재 방 참여/복귀 정책을 재구현하지 않는다.

## 10. 트랜잭션과 일관성 주의사항

- 친구 추가는 DB 트랜잭션 안에서 수행되며 예외가 발생하면 관계 저장은 rollback된다.
- `GET /friends`, 초대 가능 친구 조회, 참여자 상세 조회는 read-only 트랜잭션이다. DIRECT/GROUP 생성·초대에서 수행하는 친구 검증 조회는 각 쓰기 트랜잭션 안에서 실행된다.
- 사용자 삭제와 친구 row 정리는 같은 트랜잭션으로 묶이지 않는다. 실제로 row 정리 자체를 하지 않고 조회 조건으로 숨긴다.
- 친구 추가 성공 시 별도 캐시 무효화나 이벤트가 없으므로 다른 화면/기기의 목록은 재조회 전까지 이전 상태일 수 있다.
- `String.compareTo` 정렬은 locale-aware 정렬이 아니다. 프론트가 자체 locale 정렬을 적용하면 서버 순서와 달라질 수 있다.

### 친구 API에서 실제로 노출될 수 있는 오류 코드

| 코드 | HTTP | 발생 지점 |
| --- | --- | --- |
| `C001` | 400 | 친구 추가 username 누락/공백/50자 초과 |
| `F001` | 409 | 동일 방향 친구 관계를 서비스 사전 검사에서 발견 |
| `F002` | 400 | 자기 자신 추가 |
| `U003` | 404 | 로그인 사용자 또는 추가 대상이 없거나 삭제됨 |
| `J001` | 401 | 잘못된 서명/형식의 JWT 또는 access가 아닌 token type |
| `J002` | 401 | 만료된 AccessToken |
| `J003` | 401 | 지원하지 않는 JWT 형식 |
| `J004` | 401 | 비어 있는 JWT 문자열 |
| `J005` | 401 | Authorization 헤더 누락 |
| `J006` | 401 | Bearer 인증 scheme 불일치 |
| `S001` | 500 | 별도 매핑되지 않은 DB unique 경쟁 등 내부 예외 |

## 11. 현재 자동화 테스트가 확인하는 범위

### 11.1 서비스 테스트

- 친구 추가 성공과 owner/friend 연결 저장을 확인한다.
- 존재하지 않거나 삭제된 owner와 friend를 각각 `U003`으로 처리하는지 확인한다.
- 자기 자신 `F002`, 기존 관계 `F001` 및 각 실패 뒤 repository 저장 미호출을 확인한다.
- 목록 조회가 `friendId`, `friendUsername`, `profileImageKey`를 DTO로 변환하는지 확인한다.
- 존재하지 않거나 삭제된 로그인 사용자의 목록 조회를 차단하는지 확인한다.

### 11.2 컨트롤러 테스트

- username 필수/50자 제한과 `C001` 오류 body를 확인한다.
- 성공 및 `F001`, `F002`, `U003` HTTP 매핑을 확인한다.
- 목록 JSON 필드와 프로필 이미지 key를 확인한다.
- 이 테스트들은 `addFilters=false`로 Security filter를 끄고 principal을 직접 넣는다. 실제 JWT filter 동작은 보안 전용 테스트에서 별도로 확인한다.

### 11.3 현재 테스트가 직접 보장하지 않는 영역

- 실제 MySQL에서의 unique 경쟁 조건과 persistence 예외 매핑
- `UserFriendRepository` JPQL의 통합 실행 결과
- 친구 기반 채팅방 생성/초대 전체 트랜잭션의 통합 동작
- Redis/STOMP 메타데이터 이벤트와 친구 화면의 종단 간 동기화
- 사용자 삭제 뒤 관계 row 보존 및 active query 제외 동작의 DB 통합 검증

## 12. 현재 제공하지 않는 흐름

- 친구 요청, 승인, 거절, 차단
- 친구 삭제/복구
- 상호 친구 자동 생성
- 친구 추가 알림
- 친구 목록 pagination/search
- 관계 상태 이력

이 기능들은 현재 데이터 모델과 API에 없으므로 프론트엔드가 존재한다고 가정해서는 안 된다.
