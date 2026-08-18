# CHATFLOW.md

## 1. 문서 목적과 기준

- 이 문서는 현재 코드 기준으로 채팅방, 메시지, 읽음 상태, 파일 메시지, WebSocket/STOMP, Redis Pub/Sub 흐름을 서버 관점에서 설명한다.
- 기준 코드는 `domain/chat/**`, 채팅 파일 처리에 연결되는 `domain/file/**`, `common/messaging/**`, HTTP/WebSocket 인증 코드, JPA Repository, MyBatis Mapper다.
- 프론트엔드가 유지해야 하는 상태와 재연결 전략은 서버 동작과 섞지 않고 이 문서의 `프론트엔드 책임` 절에서 별도로 정리한다.
- 파일 메시지와 `ChatEventFile`이 채팅 상태에 결합되는 지점은 이 문서에 포함하고, 프로필 파일을 포함한 파일 저장소 전체 흐름은 [FILEFLOW.md](./FILEFLOW.md)를 함께 참고한다.
- 채팅 메시지의 수정·삭제와 채팅방 삭제 기능은 현재 없다. 채팅방 이름 변경과 `DIRECT -> GROUP` 전환은 구현되어 있다.
- 코드와 이 문서가 다르면 코드를 기준으로 판단한다.

## 2. 전체 구조

채팅 기능은 크게 다음 경로로 나뉜다.

1. 채팅방 생성·조회·초대·나가기·이름 변경은 HTTP REST API로 처리한다.
2. 텍스트 메시지 전송과 읽음 처리는 STOMP `SEND`로 처리한다.
3. 파일 메시지 전송과 파일 바이너리 조회는 HTTP API로 처리한다.
4. 서비스는 MySQL에 채팅 상태를 먼저 저장하거나 조회한다.
5. 변경 결과로 만들어진 이벤트는 컨트롤러에서 Redis Pub/Sub으로 발행한다.
6. 각 서버 인스턴스의 `RedisSubscriber`가 Redis 이벤트를 받아 로컬 WebSocket 세션에 STOMP 메시지로 전달한다.
7. Redis Pub/Sub은 영속 이벤트 로그가 아니므로 재접속 시 누락 이벤트를 재생하지 않는다. 영속 기준 상태는 MySQL이고, HTTP 조회가 재동기화 수단이다.

```text
HTTP Controller / STOMP Controller
        -> Service (@Transactional)
        -> MySQL (JPA + MyBatis)
        -> 서비스 반환 및 트랜잭션 종료
        -> RedisPublisher
        -> Redis channel
        -> 모든 인스턴스의 RedisSubscriber
        -> /topic 또는 /user/queue STOMP destination
```

### 2.1 저장소 역할

| 저장소 | 역할 |
|---|---|
| MySQL | 채팅방, 참여자 상태, 메시지, 파일 메타데이터의 영속 기준 상태 |
| 로컬 파일 시스템 | 파일 메시지의 실제 원본 및 썸네일 바이너리 저장 |
| Redis Pub/Sub | 서버 인스턴스 간 실시간 이벤트 전달. 재생·ACK·영속 보관 없음 |
| Spring simple broker | 한 서버 인스턴스에 연결된 STOMP 세션으로 topic/user destination 전달 |

### 2.2 사용 기술의 책임 분리

- JPA는 채팅방과 참여자 생성·변경, 메시지 저장, 권한 확인, 메시지 커서 갱신에 사용한다.
- MyBatis는 채팅방 목록의 다중 집계 조회와 파일 메시지 메타데이터 일괄 조회에 사용한다.
- 같은 변경 책임을 JPA와 MyBatis에 중복 구현하지 않는다. MyBatis 경로는 현재 읽기 전용이다.
- Redis 채널은 DB 변경 자체를 보장하지 않는다. DB 트랜잭션과 Redis 발행 사이에 outbox나 분산 트랜잭션은 없다.

## 3. 외부 인터페이스 요약

### 3.1 HTTP API

| Method | 경로 | 역할 | 성공 응답 |
|---|---|---|---|
| `GET` | `/chatRooms` | 내 활성 채팅방 목록 조회 | `200`, 배열 |
| `GET` | `/chatRooms/{chatRoomId}/messages?offsetMessageId=` | 표시 가능한 메시지 최대 100개 조회 | `200`, 배열 |
| `GET` | `/chatRooms/{chatRoomId}/readStatuses` | 현재 활성 참여자의 읽지 않음 시작점 조회 | `200`, 배열 |
| `POST` | `/directChatRooms` | 1대1 채팅방 생성 또는 기존 방 재입장 | `200`, `chatRoomId` |
| `POST` | `/groupChatRooms` | 단체 채팅방 생성 | `200`, `chatRoomId` |
| `POST` | `/directChatRooms/{chatRoomId}/invites` | 1대1 방에 신규 사용자를 초대하고 단체방으로 전환 | `200`, body 없음 |
| `POST` | `/groupChatRooms/{chatRoomId}/invites` | 단체방 신규 초대 또는 나간 참여자 복귀 | `200`, body 없음 |
| `POST` | `/chatRooms/{chatRoomId}/leave` | 채팅방 나가기 | `200`, body 없음 |
| `PATCH` | `/chatRooms/{chatRoomId}/name` | 단체방 공통 이름 변경 | `200`, body 없음 |
| `PATCH` | `/chatRooms/{chatRoomId}/customName` | 내 개인 채팅방 이름 변경 | `200`, body 없음 |
| `GET` | `/chatRooms/{chatRoomId}/invitableFriends` | 현재 방에 초대 가능한 내 친구 조회 | `200`, 배열 |
| `GET` | `/chatRooms/{chatRoomId}/members` | 화면에 표시할 참여자 상세 조회 | `200`, 배열 |
| `POST` | `/chatRooms/{chatRoomId}/files` | 파일 메시지 업로드 및 전송 | `200`, body 없음 |
| `GET` | `/media/messages/{chatMessageId}/files/{fileOrder}?storedFileVariant=` | 파일 원본 또는 썸네일 조회 | `200`, 파일 스트림 |

- `/media/**`를 제외한 위 HTTP 채팅 API는 AccessToken 인증 필터를 거친다.
- `/media/**`는 HTTP 보안 whitelist에 포함되어 있지만, 파일 서비스가 `mediaToken` 쿠키를 직접 파싱하고 메시지 접근 권한을 검사한다.
- REST 요청의 인증 주체는 AccessToken `sub`에서 만든 `AuthenticatedUser.userId`다.

### 3.2 STOMP destination

| 방향 | destination | 역할 |
|---|---|---|
| 연결 | `/ws` | SockJS가 활성화된 WebSocket/STOMP 연결 엔드포인트 |
| Client -> Server | `/app/chatRooms/{chatRoomId}/message` | 텍스트 메시지 전송 |
| Client -> Server | `/app/chatRooms/{chatRoomId}/read` | 메시지 읽음 커서 전진 |
| Server -> Subscribers | `/topic/chatRooms/{chatRoomId}` | 채팅방 메시지 및 읽음 이벤트 |
| Server -> User | `/user/queue/chatRooms/list` | 사용자별 채팅방 목록 변경 이벤트 |
| Server -> User | `/user/queue/users/metadata` | 사용자 이름·프로필 이미지 변경 이벤트 |
| Server -> User | `/user/queue/errors` | 구독, 메시지 전송, 읽음 처리 오류 |

### 3.3 Redis channel

| Redis channel | Redis payload | STOMP 변환 결과 |
|---|---|---|
| `chat:room:{roomId}` | 단일 `ChatEvent` JSON | `/topic/chatRooms/{roomId}` 및 필요 시 사용자별 `MESSAGE_SENT` 목록 이벤트 |
| `chat:room-list` | `ChatRoomListEvent` JSON 배열 | 각 항목의 `receiverUserId`에 해당하는 `/user/queue/chatRooms/list` |
| `user:metadata` | 단일 `UserMetadataEvent` JSON | `eventUserIds` 각각의 `/user/queue/users/metadata` |

## 4. 도메인 모델과 불변식

### 4.1 `ChatRoom`

- `chatRoomId`는 MySQL `IDENTITY` PK다.
- `chatRoomType`은 `DIRECT` 또는 `GROUP`이다.
- `DIRECT`는 `directUser1`, `directUser2`를 가진다.
- 두 사용자 ID 중 큰 값을 `directUser1`, 작은 값을 `directUser2`로 저장한다.
- `(chat_room_type, direct_user1_id, direct_user2_id)`에 유니크 제약이 있어 동일한 두 사용자의 `DIRECT` 방 중복 생성을 막는다.
- `GROUP`의 `roomName`은 선택값이며 최대 100자다.
- `DIRECT`를 초대 API로 `GROUP`으로 바꿀 때 타입을 바꾸고 `directUser1`, `directUser2`를 `null`로 만든다. 이후 원래 두 사용자가 새 `DIRECT` 방을 만들면 전환된 그룹방과 별도의 방이 생성될 수 있다.
- 마지막 사용자가 나가더라도 `ChatRoom` 자체를 삭제하는 로직은 없다.

### 4.2 `ChatRoomUser`

- `(chat_room_id, user_id)`에 유니크 제약이 있어 방과 사용자당 참여 행은 하나만 유지한다.
- 참여 행은 나갈 때 삭제하지 않는다. 상태만 `ACTIVE`에서 `LEFT`로 바꾼다.
- `chatRoomUserRole`은 `OWNER` 또는 `MEMBER`다.
- 새 `DIRECT` 방의 두 참여자는 모두 `MEMBER`다.
- 새 `GROUP` 방의 생성자는 `OWNER`, 초대된 사용자는 `MEMBER`다.
- `joinedAt`은 최초 참여 또는 가장 최근 복귀 시각이다.
- 새 참여 행의 `visibleStartMessageId`, `unreadStartMessageId`는 `0`이다.
- `leaveChatRoom()`은 상태를 `LEFT`로 바꾸고 개인 이름 `customRoomName`을 `null`로 초기화한다. 표시·읽음 커서는 변경하지 않는다.
- `joinChatRoom(boundaryMessageId)`는 상태를 `ACTIVE`로 바꾸고 `joinedAt`을 현재 시각으로 갱신하며, 두 커서를 모두 경계 메시지 ID로 설정한다.

### 4.3 메시지 표시·읽음 경계

- `visibleStartMessageId`는 그 ID를 포함한 이후 메시지만 사용자에게 표시한다는 경계다.
- `unreadStartMessageId`는 그 ID를 포함한 이후 메시지가 읽지 않은 메시지라는 경계다.
- 메시지 ID가 `unreadStartMessageId`보다 작으면 읽은 상태이고, 크거나 같으면 읽지 않은 상태다.
- 재입장 시 두 경계를 같게 맞춰 재입장 이전 메시지를 숨기고, 재입장 계기가 된 메시지부터 읽지 않은 상태로 만든다.
- 기존 `DIRECT` 방을 명시적으로 다시 열 때는 최근 메시지 ID `+ 1`을 경계로 사용하므로 기존 메시지는 모두 숨겨지고 초기 안 읽은 개수는 0이다.
- 읽음 처리는 커서를 앞으로만 이동시키는 조건부 UPDATE를 사용한다.

### 4.4 `ChatMessage`

- `chatMessageId`는 전체 `chat_message` 테이블에서 증가하는 MySQL `IDENTITY` PK다.
- 채팅방별 별도 시퀀스가 아니므로 한 방 안에서 ID가 연속적일 필요는 없다.
- 서버의 메시지 정렬, 최신 메시지, 페이지 커서는 `createdAt`이 아니라 `chatMessageId`를 기준으로 한다.
- `content`는 DB 기준 `nullable = false`, 최대 2000자다.
- 발신자는 삭제되지 않고 soft-delete 상태가 될 수 있다. 메시지 행과 발신자 FK는 유지된다.
- 메시지 수정, 삭제, soft-delete 필드는 현재 없다.

| `ChatMessageType` | 생성 경로 | `content` |
|---|---|---|
| `TEXT` | STOMP 텍스트 전송 | 클라이언트가 보낸 문자열 |
| `FILE` | HTTP 파일 메시지 전송 | `파일 {개수}개` |
| `JOIN_TEXT` | DIRECT -> GROUP 전환 또는 GROUP 초대 | 서버가 만든 참여 안내문 |
| `LEAVE_TEXT` | GROUP 나가기 | `{username}님이 채팅방에서 나가셨습니다.` |

### 4.5 `StoredFile`

- 파일 메시지와 DB FK로 직접 연결하지 않고 `fileKey = "chat-message:{chatMessageId}"` 문자열로 묶는다.
- `fileOrder`는 한 파일 메시지 안에서 1부터 시작한다.
- `fileCategory`는 `IMAGE`, `VIDEO`, `FILE`이다.
- `storedFileVariant`는 `ORIGINAL`, `THUMBNAIL`이다.
- 이미지와 영상은 원본 및 썸네일 행을 저장한다.
- 일반 파일은 `ORIGINAL` 행만 저장한다.
- 실제 파일 이름은 UUID 기반이며, 원래 파일명은 `originalFileName`에 별도로 저장한다.

## 5. 인증과 채팅방 권한

### 5.1 HTTP 인증

1. 보호 API는 `Authorization: Bearer {accessToken}`을 받는다.
2. `JwtSecurityFilter`가 서명, 만료, JWT 형식, `type=access`를 검증한다.
3. 성공하면 `sub=userId`, `sid`로 `AuthenticatedUser`를 만들고 SecurityContext에 저장한다.
4. AccessToken은 Redis 세션과 대조하지 않는다.
5. 채팅 서비스는 현재 사용자가 DB에 존재하고 삭제되지 않았는지 다시 확인한다.
6. 방 단위 API는 요청자의 `ChatRoomUser` 존재와 `ACTIVE` 상태를 추가 확인한다.

### 5.2 STOMP 연결 인증

1. HTTP WebSocket/SockJS 엔드포인트 `/ws` 자체는 GET whitelist에 포함되어 있다.
2. 클라이언트는 STOMP `CONNECT` native header에 정확히 `Authorization: Bearer {accessToken}`을 보낸다.
3. `JwtChannelInterceptor`는 `CONNECT` 프레임에서만 토큰을 검증한다.
4. 성공하면 토큰의 사용자 ID로 `StompPrincipal`을 만들고 세션 Principal로 저장한다.
5. Principal 이름은 사용자 ID 문자열이며 `@MessageMapping`의 `Principal`과 `/user/**` 라우팅에 공통 사용된다.
6. AccessToken의 Redis 대조나 사용자 DB 조회는 CONNECT 단계에서 하지 않는다.
7. 연결 후 각 `SEND` 프레임마다 JWT 만료를 다시 검사하지 않는다. 대신 메시지·읽음 서비스에서 현재 사용자 삭제 여부와 방 참여 상태를 검사한다.

### 5.3 채팅방 topic 구독 검증

1. `ChatRoomSubscriptionInterceptor`는 `SUBSCRIBE` 프레임만 본다.
2. destination이 정규식 `^/topic/chatRooms/(\d+)$`와 정확히 일치할 때만 방 권한을 검사한다.
3. Principal이 없으면 `W001 WEBSOCKET_UNAUTHENTICATED` 예외가 발생한다.
4. Principal이 있으면 `ChatRoomUser`가 `ACTIVE`이고 사용자도 삭제되지 않았는지 `existsActiveMember()`로 확인한다.
5. 권한이 없으면 `/user/queue/errors`로 `CR010 CHAT_ROOM_ACCESS_DENIED`를 보내고 해당 SUBSCRIBE 프레임을 `null`로 반환해 구독만 차단한다.
6. `/user/queue/**`와 다른 topic은 이 인터셉터의 방 권한 검사 대상이 아니다.

중요한 현재 동작:

- 권한 검사는 구독을 새로 만들 때만 수행된다.
- 사용자가 방을 나가거나 삭제되어도 서버가 이미 생성된 topic 구독을 강제로 해제하는 로직은 없다.
- 이미 구독 중인 세션은 연결이나 구독이 유지되는 동안 이후 topic 이벤트를 받을 수 있다.
- 권한 없는 SUBSCRIBE 프레임은 그 세션에서만 차단하지만, 오류는 session ID 없이 `convertAndSendToUser()`로 전송한다. 같은 사용자 ID의 여러 세션이 `/user/queue/errors`를 구독했다면 모두 `CR010`을 받을 수 있다. 메시지 handler의 `@SendToUser(broadcast=false)` 오류가 현재 세션에만 전달되는 것과 다르다.
- 따라서 방을 나간 클라이언트의 즉시 `UNSUBSCRIBE` 처리가 필요하며, 이 제한은 서버 보안 관점에서도 인지해야 한다.

### 5.4 서비스 공통 방 접근 검사

대부분의 방 단위 서비스는 다음 순서를 반복한다.

1. `(chatRoomId, userId)`의 `ChatRoomUser`를 조회한다. 없으면 `CR010`이다.
2. 상태가 `LEFT`면 `CR010`이다.
3. 연결된 `User.deleted`가 `true`면 `U003`이다.
4. 기능별 방 타입, 방장 권한, 메시지 가시성 조건을 추가 검사한다.

`findAllChatRooms()`는 방별 참여 행보다 먼저 사용자 존재·삭제 여부를 확인한 다음 내 `ACTIVE` 방만 조회한다.

## 6. WebSocket 및 Redis 이벤트 전달

### 6.1 브로커 설정

- 클라이언트가 서버로 보내는 application prefix는 `/app`이다.
- 서버 broker destination은 `/topic`, `/queue`다.
- 사용자별 destination prefix는 `/user`다.
- `/ws`는 SockJS fallback을 활성화하고 WebSocket endpoint의 allowed origin pattern은 `*`다.
- 이 endpoint 등록값과 별개로 HTTP 보안 CORS는 `http://localhost:5173`, `https://jiangxy.github.io` 두 origin만 허용한다. 실제 cross-origin 연결은 HTTP CORS 제한도 통과해야 한다.
- HTTP whitelist는 `GET /ws/**`만 공개한다. SockJS의 POST 기반 fallback transport 요청은 보호 체인으로 들어가 HTTP `Authorization: Bearer ...`를 요구하며, 그 뒤 STOMP 프레임에서 해석되는 CONNECT native header만으로는 이 HTTP 요청을 통과시킬 수 없다.
- 사용자별 전송은 `convertAndSendToUser(userId문자열, "/queue/...", payload)`로 수행한다.
- 동일한 사용자 ID로 여러 WebSocket 세션이 연결되어 있으면 일반 사용자별 이벤트는 그 사용자 destination에 연결된 세션들로 라우팅될 수 있다.

### 6.2 `ChatEvent` payload

`ChatEvent`는 방 topic용 union 형태 DTO다. 이벤트 종류에 사용하지 않는 필드는 `null`이다.

| 필드 | `MESSAGE_SENT` | `MESSAGE_READ` |
|---|---|---|
| `chatEventType` | `MESSAGE_SENT` | `MESSAGE_READ` |
| `roomId` | 방 ID | 방 ID |
| `senderId`, `senderName`, `senderProfileImageKey` | 발신자 정보 | `null` |
| `chatEventFiles` | 파일 메시지면 원본 파일 메타데이터 목록, 그 외 `null` | `null` |
| `content`, `messageId`, `chatMessageType`, `createdAt` | 메시지 정보 | `null` |
| `readerUserId`, `unreadStartMessageId` | `null` | 읽은 사용자와 새 커서 |
| `eventUserIds` | Redis 내부 목록 이벤트 수신자 | 내부에서는 빈 목록 |

- `eventUserIds`는 Redis 내부 라우팅용이다.
- `RedisSubscriber`는 Redis payload를 받은 뒤 `eventUserIds`를 읽고 `clearEventUserIds()`로 `null` 처리한 후 방 topic에 보낸다.
- 따라서 `/topic/chatRooms/{roomId}`에서 프론트가 받는 `ChatEvent.eventUserIds`는 수신자 목록으로 사용할 수 없다.
- `MESSAGE_SENT`의 내부 `eventUserIds` 각각에는 별도의 `ChatRoomListEvent.MESSAGE_SENT`가 전달된다.
- `MESSAGE_READ`, 초대·퇴장 시스템 메시지는 내부 `eventUserIds`가 빈 목록이므로 이 자동 목록 이벤트가 발생하지 않는다.

### 6.3 `ChatEventFile` payload

파일 메시지의 `chatEventFiles`와 HTTP 메시지 조회의 같은 필드는 다음 값만 제공한다.

| 필드 | 의미 |
|---|---|
| `fileOrder` | 메시지 안의 1-based 순서 |
| `fileCategory` | `IMAGE`, `VIDEO`, `FILE` |
| `originalFileName` | 업로드 시 원래 파일명 |
| `fileSize` | 원본 파일 크기 |

- 실제 저장 파일명, `fileKey`, content type은 외부 payload에 노출하지 않는다.
- 바이너리 URL은 `messageId`와 `fileOrder`, 원하는 variant로 프론트가 구성한다.

### 6.4 `ChatRoomListEvent` payload

목록 이벤트도 이벤트 타입별로 필요한 필드만 채우며 나머지는 `null`이다.

전체 DTO 필드는 `eventType`, `roomId`, `roomType`, `receiverUserId`, `baseRoomName`, `customRoomName`, `myRole`, `memberCount`, `previewUsers`, `lastMessagePreview`, `messageId`, `lastActivityAt`, `unreadStartMessageId`, `unreadCount`다. `previewUsers`의 각 항목은 `userId`, `username`, `profileImageKey`를 가진다.

| 이벤트 | 채워지는 핵심 필드 | 서버 발생 시점 |
|---|---|---|
| `ROOM_ADDED` | 방 기본 정보, 내 역할, 인원수, 미리보기, 최근 메시지, 읽음 커서·개수 | 새 방 생성, 재입장, 신규 초대 |
| `ROOM_CHANGED` | 방 타입·이름·내 역할, 인원수, 미리보기, 최근 메시지 | 초대/전환, GROUP 퇴장 |
| `ROOM_NAME_CHANGED` | `roomId`, `receiverUserId`, `baseRoomName`, `customRoomName` | 공통 또는 개인 이름 변경 |
| `ROOM_REMOVED` | `roomId`, `receiverUserId` | 요청자 나가기 |
| `MESSAGE_SENT` | `roomId`, 최근 내용, `messageId`, `lastActivityAt` | 일반 TEXT/FILE `ChatEvent`에서 RedisSubscriber가 변환 |
| `MESSAGE_READ` | `roomId`, `receiverUserId`, `unreadStartMessageId`, `unreadCount` | 읽음 요청 처리 |

세부 사항:

- 서버가 직접 만든 목록 이벤트 배열은 `chat:room-list`에 한 번 발행된다.
- `RedisSubscriber`는 배열을 순회하고 각 `receiverUserId`로 전송한다.
- 자동 변환된 `MESSAGE_SENT` payload의 `receiverUserId`는 `null`이다. 이미 `convertAndSendToUser()` 호출 대상이 수신자를 결정한다.
- `ROOM_CHANGED`와 `MESSAGE_SENT`에는 `unreadCount`가 없다.
- 목록 이벤트의 `receiverUserId`는 topic 이벤트의 `eventUserIds`와 달리 클라이언트 전송 전에 제거하지 않는다.

### 6.5 사용자 메타데이터 이벤트와 채팅 UI

- 이름 변경 또는 프로필 이미지 변경은 `user:metadata` Redis 채널을 사용한다.
- 수신 destination은 `/user/queue/users/metadata`다.
- 이벤트 타입은 `USERNAME_UPDATED`, `USER_PROFILE_IMAGE_UPDATE`다.
- 전체 DTO 필드는 `userMetadataEventType`, `userId`, `username`, `userProfileImageKey`, `eventUserIds`다.
- 이름 변경은 `username`만, 프로필 변경은 `userProfileImageKey`만 채우며 반대쪽 필드는 `null`이다.
- Redis 내부 수신자 목록 `eventUserIds`는 STOMP 전송 전에 `null`로 지운다.
- 수신자는 변경 사용자와 같은 채팅방 이력이 있는 사용자 중 현재 `ACTIVE`이고 삭제되지 않은 다른 사용자 ID를 조회해 만든다.
- 채팅방 목록의 `previewUsers`나 이미 렌더링한 발신자 메타데이터를 최신화하려면 프론트가 이 큐도 구독해야 한다.
- 이벤트에는 room ID나 서버가 다시 계산한 preview 배열이 없다. 이름 변경으로 GROUP의 이름순 상위 4명 구성 자체가 달라지면 기존 preview 항목의 문자열만 교체해서는 HTTP 목록 결과를 정확히 재현할 수 없다.

### 6.6 발행 순서와 전달 특성

코드가 RedisPublisher를 호출하는 순서는 다음과 같다.

| 동작 | 호출 순서 |
|---|---|
| TEXT 전송 | 직접 생성한 목록 이벤트 -> 방 `ChatEvent` |
| FILE 전송 | 직접 생성한 목록 이벤트 -> 방 `ChatEvent` |
| DIRECT/GROUP 초대 | 목록 이벤트 -> 방 `ChatEvent` |
| 나가기 | 목록 이벤트 -> 방 `ChatEvent`가 존재하면 발행 |
| 읽음 | 방 `ChatEvent` -> 목록 이벤트 |
| 방 생성/이름 변경 | 목록 이벤트만 발행 |

- 일반 TEXT/FILE 전송에서 서비스가 직접 만든 목록 이벤트 배열은 대개 비어 있지만, 컨트롤러는 그래도 먼저 `chat:room-list`에 `[]`를 발행한다. 이 발행이 실패하면 DB 메시지는 이미 커밋된 상태에서 다음 방 `ChatEvent` 발행이 실행되지 않는다.
- 방 `ChatEvent`를 받은 `RedisSubscriber` 내부 STOMP 호출 순서는 방 `/topic/chatRooms/{id}` 전송 후 `eventUserIds`별 사용자 목록 `MESSAGE_SENT` 전송이다. 따라서 위 표의 “목록 이벤트 -> 방 이벤트”는 컨트롤러의 두 Redis publish 호출 순서이며, 방 이벤트에서 자동 파생되는 목록 `MESSAGE_SENT`의 STOMP 순서를 뜻하지 않는다.
- 호출 순서는 위와 같지만 `chat:room-list`와 `chat:room:{id}`는 서로 다른 Redis 채널이다.
- 프론트에서 두 STOMP destination의 도착 순서를 하나의 전역 순서로 가정해서는 안 된다.
- Redis Pub/Sub은 소비 확인, 재시도, 과거 이벤트 재생을 제공하지 않는다.
- `RedisSubscriber`가 역직렬화 또는 STOMP 전달 중 예외를 만나면 `S001`로 로그만 남기고 해당 이벤트를 클라이언트에 재전송하지 않는다.

## 7. 텍스트 메시지 전송 흐름

### 7.1 요청

- destination: `/app/chatRooms/{chatRoomId}/message`
- payload:

```json
{
  "content": "메시지 내용"
}
```

- `ChatMessageRequest.content`에는 Bean Validation 어노테이션이 없다.
- STOMP handler에도 `@Valid`가 없다.
- 따라서 빈 문자열·길이에 대한 명시적인 애플리케이션 검사는 현재 없으며, `null`과 2000자 초과 여부는 최종적으로 엔티티/DB 제약 또는 런타임 예외의 영향을 받는다.

### 7.2 공통 서버 처리

1. `ChatMessageStompController`가 Principal 이름을 Long 사용자 ID로 변환한다.
2. `ChatMessageService.saveMessage()` 트랜잭션을 시작한다.
3. 요청자의 방 참여 행과 방·사용자를 fetch join으로 조회한다.
4. 참여 행이 없거나 `LEFT`면 `CR010`이다.
5. 요청 사용자가 삭제 상태면 `U003`이다.
6. `ChatMessageType.TEXT` 메시지를 저장한다.
7. 생성된 `chatMessageId`와 `createdAt`을 이벤트 기준값으로 사용한다.
8. 방 타입에 따라 목록 이벤트 수신자와 DIRECT 상대방 복귀 여부를 정한다.
9. 서비스가 `SaveChatMessageResult`를 반환하고 DB 트랜잭션이 종료된다.
10. 컨트롤러가 직접 생성된 목록 이벤트를 먼저 Redis에 발행한다.
11. 현재 구현상 한 개인 `ChatEvent`를 이어서 `chat:room:{roomId}`에 발행한다.

### 7.3 `DIRECT` 처리

1. 해당 방의 삭제되지 않은 `ChatRoomUser`와 User를 모두 조회한다.
2. 요청자 이외의 행을 상대방으로 찾는다.
3. 삭제된 상대방은 조회 결과에서 제외되므로 자동 복귀시키지 않는다.
4. 상대방이 `LEFT`면 저장된 새 메시지 ID를 경계로 `joinChatRoom()`을 호출한다.
5. 상대방의 `joinedAt`이 현재 시각으로 바뀌고 상태가 `ACTIVE`가 된다.
6. 상대방의 `visibleStartMessageId`, `unreadStartMessageId`가 새 메시지 ID가 된다.
7. 상대방에게 다음 값의 `ROOM_ADDED`를 만든다.
   - 인원수 `2`
   - preview user는 메시지 발신자 1명
   - 최근 메시지는 방금 저장한 메시지
   - `unreadStartMessageId = 새 messageId`
   - `unreadCount = 1`
8. 이 복귀 경우 자동 `MESSAGE_SENT` 목록 이벤트의 내부 수신자는 발신자만 둔다. 상대방은 이미 전체 상태를 담은 `ROOM_ADDED`를 받기 때문이다.
9. 상대방이 이미 `ACTIVE`면 삭제되지 않은 두 참여자 ID를 자동 `MESSAGE_SENT` 목록 이벤트 수신자로 둔다.
10. 상대방이 삭제되어 조회되지 않으면 조회된 유효 사용자, 실질적으로 발신자만 목록 이벤트 수신자가 된다.

`ChatEvent`의 topic 발행 자체는 수신자 ID로 제한되지 않는다. 해당 topic을 실제로 구독 중인 세션 전체에 브로드캐스트된다.

### 7.4 `GROUP` 처리

- 현재 `ACTIVE`이고 삭제되지 않은 참여자 ID 전체를 내부 `eventUserIds`로 사용한다.
- 발신자도 포함된다.
- 모든 내부 수신자는 `/user/queue/chatRooms/list`로 최근 메시지 갱신용 `MESSAGE_SENT`를 받는다.
- 별도의 자동 읽음 처리는 없다. 발신자 자신의 메시지도 명시적인 `/read` 요청 전까지 DB unread 계산 대상이다.

### 7.5 topic `MESSAGE_SENT` 결과

topic 수신 payload에는 다음 정보가 들어간다.

- `roomId`
- 발신자 ID, 이름, 프로필 이미지 키
- `content`
- DB가 생성한 `messageId`
- `chatMessageType = TEXT`
- `createdAt`
- `chatEventFiles = null`
- `eventUserIds = null`

## 8. 파일 메시지 전송 흐름

### 8.1 요청 및 제한

- endpoint: `POST /chatRooms/{chatRoomId}/files`
- content type: `multipart/form-data`
- part 이름: `files`
- 파일은 1개 이상 30개 이하여야 한다.
- `null`, 빈 목록, `null` 항목, 빈 파일이 하나라도 있으면 `SF003`이다.
- 서비스에서 합산한 전체 크기는 `3 * 1024 * 1024 * 1024` byte 이하여야 한다. 초과하면 `SF005`다.
- 애플리케이션 multipart 설정도 `max-file-size: 3GB`, `max-request-size: 3100MB`로 요청 수신 단계의 상한을 둔다.

### 8.2 권한과 메시지 선저장

1. AccessToken 인증 사용자로 방 참여 행, 방, 사용자를 조회한다.
2. 참여 행이 없거나 `LEFT`면 `CR010`이다.
3. 사용자가 삭제 상태면 `U003`이다.
4. 파일 수와 전체 크기를 검증한다.
5. 파일 바이너리 처리 전에 `ChatMessageType.FILE`, `content = "파일 {N}개"` 메시지를 저장한다.
6. 생성된 메시지 ID로 `fileKey = "chat-message:{messageId}"`를 만든다.

### 8.3 파일별 분류와 저장

서버는 요청의 Content-Type 헤더만 믿지 않고 Apache Tika로 실제 내용을 감지한다.

#### 이미지

- JPEG, PNG, GIF, WebP로 감지된 content type만 이미지 처리 분기로 들어간다. 다른 이미지 MIME은 현재 일반 파일 분기로 처리된다.
- ImageIO reader가 실제 파일을 읽을 수 있는지 다시 확인한다.
- 허용 포맷은 `jpeg`, `png`, `gif`, `webp`다. 그 외는 `SF001`이다.
- 애니메이션 GIF/WebP도 첫 프레임만 썸네일에 사용한다.
- 원본은 감지 포맷에 맞는 UUID 파일명으로 저장한다.
- 썸네일은 최대 `320 x 320`, 종횡비 유지, JPEG 품질 `0.9`로 생성한다.
- 같은 `fileOrder`에 `ORIGINAL`과 `THUMBNAIL` `StoredFile` 두 행을 만든다.

#### 영상

- 감지 대상은 MP4 계열, QuickTime/MOV, WebM이다.
- 확장자와 content type을 MP4, MOV, WebM 중 하나로 정규화해 UUID 원본 파일명으로 저장한다.
- 시스템 PATH의 `ffmpeg`를 호출해 첫 프레임 JPEG 썸네일을 만든다.
- 썸네일은 `320 x 320` 경계 안으로 종횡비를 유지해 크기를 조정한다. 현재 FFmpeg filter에는 작은 원본의 확대를 막는 별도 조건이 없다.
- 같은 `fileOrder`에 `ORIGINAL`과 `THUMBNAIL` 두 행을 만든다.

#### 일반 파일

- 이미지·영상으로 분류되지 않은 파일은 `FileCategory.FILE`이다.
- 확장자 없는 UUID 저장 파일명으로 원본만 저장한다.
- Tika가 감지한 content type은 DB에 보관하지만 다운로드 응답은 `application/octet-stream`을 사용한다.
- `THUMBNAIL` 행은 만들지 않는다.

### 8.4 DB 메타데이터와 실패 정리

1. 모든 파일의 로컬 저장이 끝나면 `StoredFile` 목록을 `saveAll()`한다.
2. 실시간 이벤트에는 `ORIGINAL` variant만 골라 `ChatEventFile` 목록을 만든다.
3. 서비스 메서드 내부에서 예외가 발생하면 이번 요청 중 생성한 파일 경로들을 `Files.deleteIfExists()`로 정리하고 예외를 다시 던진다.
4. 정리 중 파일 삭제가 실패하면 경고 로그를 남기지만 원래 예외를 유지한다.
5. DB 변경은 `@Transactional` 범위에 있어 런타임 예외 시 롤백 대상이다.
6. 로컬 파일 시스템은 DB 트랜잭션 자원이 아니다. 특히 서비스 반환 이후 트랜잭션 commit 자체가 실패하는 경우까지 파일 정리 코드가 포괄하는 구조는 아니다.

### 8.5 참여자 복귀와 이벤트

- 파일 저장 이후의 DIRECT 상대방 자동 복귀, GROUP 수신자 선정은 TEXT 전송과 동일하다.
- 복귀 상대방은 파일 메시지 ID부터 볼 수 있고 `unreadCount = 1`인 `ROOM_ADDED`를 받는다.
- 이 `ROOM_ADDED`에는 `파일 N개`, message ID, 시각, unread 상태는 있지만 `chatEventFiles` 필드가 없다. 복귀 상대방은 자동 목록 `MESSAGE_SENT` 수신자에서도 제외되며, 정상 퇴장 때 방 topic 구독을 해제했다면 전체 파일 `ChatEvent`도 놓칠 수 있다. `ROOM_ADDED` 뒤 HTTP 메시지 목록을 조회해야 첨부 메타데이터를 복구할 수 있다.
- topic의 `MESSAGE_SENT`는 `chatMessageType = FILE`, `content = "파일 {N}개"`, `chatEventFiles = 원본 메타데이터 목록`을 가진다.
- 컨트롤러는 목록 이벤트를 먼저 발행하고 방 이벤트를 나중에 발행한 뒤 `200` body 없이 응답한다.

## 9. 메시지 목록 조회 흐름

### 9.1 요청과 권한

- endpoint: `GET /chatRooms/{chatRoomId}/messages`
- 선택 query parameter: `offsetMessageId`
- 요청자의 방 참여 행이 없거나 `LEFT`면 `CR010`이다.
- 요청 사용자가 삭제 상태면 `U003`이다.
- `offsetMessageId`가 특정 방의 메시지인지 별도로 검증하지 않는다. 숫자 경계값으로만 사용한다.

### 9.2 JPA 조회 조건

```text
roomId 일치
AND messageId >= 요청자의 visibleStartMessageId
AND (offsetMessageId가 없거나 messageId < offsetMessageId)
ORDER BY messageId DESC
LIMIT 100
```

- 메시지와 발신자를 fetch join한다.
- 첫 조회는 `offsetMessageId` 없이 최신 메시지부터 최대 100개를 받는다.
- 다음 페이지는 현재 보유 목록의 가장 작은 `messageId`를 offset으로 보낸다.
- 응답도 최신순, 즉 `messageId` 내림차순이다.
- `hasNext`, 다음 cursor, 전체 개수는 별도로 제공하지 않는다.
- 발신자가 soft-delete 상태여도 메시지는 반환한다.
- 삭제된 발신자는 `senderId`, `senderName`, `senderProfileImageKey`를 모두 `null`로 바꿔 응답한다.

### 9.3 파일 메타데이터 조합

1. 조회된 100개 이내 메시지 중 타입이 `FILE`인 메시지의 `chat-message:{messageId}` 키를 만든다.
2. FILE 메시지가 하나라도 있으면 MyBatis로 모든 키의 `ORIGINAL` `StoredFile` 행을 한 번에 조회한다.
3. 결과는 `fileKey`, `fileOrder` 순으로 정렬한다.
4. `fileKey`별 `ChatEventFile` 목록으로 그룹화한다.
5. 각 메시지 응답의 `chatEventFiles`에 해당 목록을 결합한다.
6. 일반 메시지 또는 파일 메타데이터가 없는 메시지는 `chatEventFiles`가 `null`일 수 있다.

### 9.4 응답 필드

각 `ChatMessageListResponseDto`는 다음 값을 가진다.

- `messageId`
- `chatMessageType`
- `content`
- `senderId`, `senderName`, `senderProfileImageKey`
- `createdAt`
- `chatEventFiles`

## 10. 읽음 상태 흐름

### 10.1 읽음 상태의 의미

- 서버는 메시지별 읽음 boolean을 저장하지 않는다.
- 사용자별 `unreadStartMessageId` 하나만 저장한다.
- 특정 메시지 `M`은 `M.messageId < unreadStartMessageId`이면 읽은 것으로 해석한다.
- 메시지 ID는 전체 테이블 PK이므로 `unreadStartMessageId`가 실제로 그 방에 존재하는 메시지 ID일 필요는 없다. 마지막 메시지를 읽으면 `마지막 messageId + 1`이 된다.

### 10.2 활성 참여자 읽음 범위 조회

- endpoint: `GET /chatRooms/{chatRoomId}/readStatuses`
- 요청자는 해당 방의 삭제되지 않은 `ACTIVE` 참여자여야 한다.
- 서버는 그 방의 삭제되지 않은 `ACTIVE` 참여자만 다시 조회한다.
- 응답 항목은 `userId`, `unreadStartMessageId`다.
- `DIRECT` 상대방이 `LEFT`면 참여자 상세 조회에는 나타날 수 있지만 read status 응답에서는 제외된다.
- 응답 순서는 Repository query에서 별도로 지정하지 않는다.

### 10.3 읽음 요청

- destination: `/app/chatRooms/{chatRoomId}/read`
- payload:

```json
{
  "readMessageId": 123
}
```

- DTO와 handler에 명시적인 Bean Validation은 없다.

### 10.4 서버 처리 순서

1. Principal에서 사용자 ID를 구한다.
2. 방 참여 행과 사용자를 조회한다.
3. 참여 행이 없거나 `LEFT`면 `CR010`이다.
4. 사용자가 삭제 상태면 `U003`이다.
5. `readMessageId`가 같은 `chatRoomId`에 실제 존재하는지 검사한다. 없으면 `CM001`이다.
6. `readMessageId < visibleStartMessageId`이면 보이지 않는 메시지이므로 `CM002`다.
7. `newUnreadStartMessageId = readMessageId + 1`을 계산한다.
8. 다음 조건의 UPDATE를 수행한다.

```text
UPDATE ChatRoomUser
SET unreadStartMessageId = newValue
WHERE chatRoomUserId = 내 참여 행 ID
  AND unreadStartMessageId < newValue
```

9. JPA 영속성 컨텍스트를 clear한 뒤 참여 행을 다시 조회해 실제 저장된 커서를 얻는다.
10. 같은 방에서 `messageId >= 실제 커서`인 메시지 수를 세어 `unreadCount`를 구한다.
11. 방 topic용 `MESSAGE_READ`와 요청자 목록용 `MESSAGE_READ`를 만든다.
12. 컨트롤러는 방 이벤트를 먼저 발행하고 목록 이벤트를 나중에 발행한다.

### 10.5 동시성 특성

- 더 과거 메시지에 대한 늦은 요청은 조건부 UPDATE 조건을 통과하지 못하므로 커서를 뒤로 돌리지 않는다.
- UPDATE 결과 행 수를 사용하지 않고 영속성 컨텍스트를 clear한 뒤 같은 트랜잭션에서 저장 커서를 다시 읽으므로 이벤트에는 그 재조회 값이 들어간다. 별도 row lock이나 `@Version`으로 전체 동시 작업을 직렬화하는 구조는 아니다.
- `MESSAGE_READ` topic 이벤트에는 `readerUserId`, 최종 `unreadStartMessageId`가 들어간다.
- 사용자별 목록 이벤트에는 최종 커서와 그 시점에 계산한 `unreadCount`가 들어간다.
- 읽음 트랜잭션과 동시에 새 메시지가 commit되면 이벤트의 `unreadCount`는 이후 상태와 순간적으로 달라질 수 있다. 다음 읽음 처리 또는 HTTP 목록 재조회로 보정하는 느슨한 정합성 구조다.

## 11. `DIRECT` 채팅방 생성 또는 재입장

### 11.1 요청

- endpoint: `POST /directChatRooms`
- body: `{ "friendId": number }`
- `friendId`는 `@NotNull`이다.

### 11.2 검증

1. 요청 사용자가 존재하고 삭제되지 않았는지 검사한다. 실패하면 `U003`이다.
2. 자기 자신이면 `CR002`다.
3. 요청자 -> 상대방의 친구 행이 존재하고 상대방이 삭제되지 않았는지 검사한다. 실패하면 `CR004`다.
4. 두 ID를 큰 값/작은 값으로 정규화한다.
5. 동일한 pair의 현재 `DIRECT` 방을 조회한다. 이미 GROUP으로 전환된 과거 방은 조회되지 않는다.

### 11.3 기존 방이 없을 때

1. `DIRECT` `ChatRoom`을 저장한다.
2. 두 사용자의 `ChatRoomUser`를 `MEMBER`, `ACTIVE`로 저장한다.
3. 두 사용자의 `joinedAt`은 생성 시각이고 두 커서는 `0`이다.
4. 각 사용자에게 상대방 한 명을 `previewUsers`로 담은 `ROOM_ADDED`를 만든다.
5. `memberCount = 2`, 최근 메시지와 message ID는 `null`, `lastActivityAt = 각 joinedAt`, `unreadStartMessageId = 0`, `unreadCount = 0`이다.
6. 목록 이벤트를 Redis에 발행한다.
7. HTTP 응답은 생성된 `chatRoomId`다.

### 11.4 기존 `DIRECT` 방이 있을 때

1. 삭제되지 않은 두 참여 행을 조회한다.
2. 방의 최대 `chatMessageId`를 조회한다.
3. 메시지가 있으면 `boundary = latestMessageId + 1`, 없으면 `0`이다.
4. `LEFT`인 각 참여자에 대해 `joinChatRoom(boundary)`를 실행한다.
5. 복귀자에게만 `ROOM_ADDED`를 만든다.
6. 복귀 이벤트는 기존 메시지를 최근 메시지로 보여주지 않는다.
   - `lastMessagePreview = null`
   - `messageId = null`
   - `lastActivityAt = 새 joinedAt`
   - `unreadStartMessageId = boundary`
   - `unreadCount = 0`
7. 이미 `ACTIVE`인 참여자의 상태·커서·`joinedAt`은 바꾸지 않고 이벤트도 만들지 않는다.
8. 둘 다 이미 `ACTIVE`여도 동일한 기존 `chatRoomId`를 `200`으로 반환한다.

## 12. `GROUP` 채팅방 생성

### 12.1 요청과 검증

- endpoint: `POST /groupChatRooms`
- body fields:
  - `friendIds`: `@NotNull`
  - `chatRoomName`: 선택값, 요청 문자열 길이 최대 100

처리 순서:

1. 요청 사용자가 존재하고 삭제되지 않았는지 검사한다.
2. `friendIds`를 `HashSet`으로 바꿔 중복을 제거한다.
3. 결과가 비어 있으면 `CR003`이다.
4. 요청자 자신의 ID가 있으면 `CR002`다.
5. 모든 ID가 현재 요청자의 친구이고 삭제되지 않은 사용자인지 한 번에 조회한다.
6. 조회 수와 중복 제거 후 ID 수가 다르면 `CR004`다.

### 12.2 생성과 이벤트

1. `chatRoomName`이 `null` 또는 blank면 `roomName = null`로 저장한다.
2. 값이 있으면 `strip()`한 문자열을 저장한다.
3. 요청자는 `OWNER`, 친구들은 `MEMBER`인 `ACTIVE` 참여 행을 만든다.
4. 두 커서는 모두 `0`이다.
5. 방 생성 자체로 `JOIN_TEXT` 메시지를 저장하지 않는다.
6. 각 참여자 기준으로 자신을 제외한 사용자를 Java 문자열 이름순으로 정렬한다.
7. 앞의 최대 4명을 `previewUsers`로 둔다.
8. 각 참여자에게 전체 인원수와 자기 역할을 포함한 `ROOM_ADDED`를 만든다.
9. 최근 메시지는 `null`, `lastActivityAt`은 각자의 `joinedAt`, 읽음 커서·개수는 `0`이다.
10. 목록 이벤트를 발행하고 `chatRoomId`를 반환한다.

- 현재 그룹 인원수 상한은 서비스에 없다.
- 현재 그룹 초대 권한은 방장 전용이 아니다. 생성 이후 초대 API는 모든 `ACTIVE` 멤버가 호출할 수 있다.

## 13. `DIRECT` 방 초대 및 `GROUP` 전환

### 13.1 요청과 공통 검증

- endpoint: `POST /directChatRooms/{chatRoomId}/invites`
- body: `{ "friendIds": [ ... ] }`
- `friendIds`는 `@NotNull`이고, 서비스가 중복을 제거한다.
- 빈 목록이면 `CR006`, 요청자 ID 포함이면 `CR007`이다.
- 요청자가 삭제되지 않은 `ACTIVE` 참여자여야 한다.
- 방 타입이 `DIRECT`가 아니면 `CR015 GROUP_CHAT_ROOM_INVITE_API_REQUIRED`다.

### 13.2 기존 참여자와 신규 사용자 판별

1. 방의 삭제되지 않은 기존 참여자를 조회한다.
2. 결과가 정확히 2명이 아니면 기존 상대가 삭제된 것으로 취급해 `CR017`이다.
3. 요청 목록에서 기존 두 참여자 ID를 제외해 `newInviteeIds`를 만든다.
4. 신규 ID가 하나도 없으면 `CR013`이다.
5. 신규 사용자만 요청자의 현재 친구이며 삭제되지 않았는지 검사한다. 실패하면 `CR005`다.
6. 기존 두 참여자 중 `LEFT`인 사람은 요청 목록 포함 여부와 관계없이 복귀 대상으로 잡는다.

즉, 기존 상대방은 신규 친구 검증 대상이 아니며, 신규 사용자가 최소 한 명 포함되어야 전환이 진행된다.

### 13.3 전환, 시스템 메시지, 멤버십

1. 기존 방 타입을 `GROUP`으로 변경하고 direct pair 컬럼을 `null`로 만든다.
2. 요청자의 역할을 `OWNER`로 바꾼다.
3. 신규 초대 사용자만 이름순으로 정렬한다.
4. 이름은 최대 10명까지 `{username}님` 형식으로 표시한다.
5. 10명을 넘으면 `외 {remainingCount}명`을 덧붙인다.
6. 생성한 문장을 요청자 발신의 `JOIN_TEXT` 메시지로 저장한다.
7. 기존 `LEFT` 참여자는 이 JOIN 메시지 ID로 복귀시킨다.
8. 신규 사용자는 `MEMBER`, `ACTIVE` 참여 행을 만들고 같은 메시지 ID로 두 경계를 설정한다.
9. 기존 상대가 복귀하더라도 JOIN 문장에 포함되는 이름은 신규 초대 사용자 이름뿐이다.

- 전환은 기존 `ChatRoom.roomName`을 새로 만들지 않으므로 전환 직후 GROUP 기본 이름은 보통 `null`이다.
- 기존 활성 참여자의 `customRoomName`도 초기화하지 않아 DIRECT에서 설정한 개인 이름을 그대로 유지한다. 기존 LEFT 참여자는 퇴장 시 이미 개인 이름이 `null`로 초기화됐고, 복귀해도 그 상태를 유지한다.

### 13.4 이벤트

1. 전체 현재 `ACTIVE`, 삭제되지 않은 참여자를 조회한다.
2. 복귀자와 신규 참여자에게 `ROOM_ADDED`를 만든다.
   - 최근 메시지는 JOIN_TEXT
   - `unreadStartMessageId = JOIN messageId`
   - `unreadCount = 1`
3. 전환 전부터 활성 상태였던 참여자에게 `ROOM_CHANGED`를 만든다.
4. 미리보기 사용자는 수신자 자신을 제외하고 이름순 최대 4명이다.
5. 요청자는 이벤트에서 `myRole = OWNER`다.
6. 목록 이벤트를 먼저 발행한다.
7. JOIN_TEXT `ChatEvent`는 내부 `eventUserIds = []`로 방 topic에만 발행한다.
8. 별도 자동 `MESSAGE_SENT` 목록 이벤트는 만들지 않는다. 목록의 최근 메시지는 이미 `ROOM_ADDED`/`ROOM_CHANGED`에 포함되어 있다.

## 14. `GROUP` 채팅방 초대 및 복귀

### 14.1 검증

- endpoint: `POST /groupChatRooms/{chatRoomId}/invites`
- 빈 목록은 `CR006`, 요청자 자신 포함은 `CR007`이다.
- 요청자가 삭제되지 않은 `ACTIVE` 참여자여야 한다.
- 방 타입이 `GROUP`이 아니면 `CR014 DIRECT_CHAT_ROOM_INVITE_API_REQUIRED`다.
- 모든 요청 ID는 현재 요청자의 친구이며 삭제되지 않은 사용자여야 한다. 실패하면 `CR005`다.
- 방장 여부는 검사하지 않는다. 현재 구현은 `ACTIVE` 멤버 누구나 자기 친구를 초대할 수 있다.

### 14.2 대상 분류

1. 요청 ID에 해당하는 기존 `ChatRoomUser`를 조회한다.
2. 기존 행이 `LEFT`면 복귀 대상이다.
3. 기존 행이 없으면 신규 초대 대상이다.
4. 기존 행이 `ACTIVE`면 별도 처리하지 않는다.
5. 복귀자와 신규 사용자가 모두 없으면 `CR016`이다.

### 14.3 저장과 이벤트

1. 복귀자와 신규 사용자의 이름을 합쳐 이름순으로 정렬한다.
2. 최대 10명 이름과 필요 시 나머지 인원수를 조합해 `JOIN_TEXT`를 저장한다.
3. 복귀자는 JOIN 메시지 ID로 `joinChatRoom()`한다.
4. 신규 사용자는 `MEMBER`, `ACTIVE` 행을 만들고 같은 메시지 ID로 두 경계를 맞춘다.
5. 복귀자·신규 사용자에게 최근 JOIN 메시지, 읽음 커서, `unreadCount = 1`을 포함한 `ROOM_ADDED`를 보낸다.
6. 기존 활성 사용자에게 인원수, 역할, 미리보기, 최근 JOIN 메시지를 포함한 `ROOM_CHANGED`를 보낸다.
7. JOIN_TEXT 방 이벤트는 topic에 발행하지만 내부 `eventUserIds`는 비어 있어 추가 목록 `MESSAGE_SENT`는 없다.

## 15. 채팅방 나가기

### 15.1 요청과 기본 변경

- endpoint: `POST /chatRooms/{chatRoomId}/leave`
- body의 `nextOwnerId` 필드는 선택값이다. 다만 컨트롤러의 `@RequestBody` 자체는 required이므로 필드가 필요 없을 때도 빈 JSON 객체를 보내는 형태다.
- 요청자는 삭제되지 않은 `ACTIVE` 참여자여야 한다.
- 서비스는 먼저 요청자의 상태를 `LEFT`로 바꾸고 `customRoomName`을 `null`로 만든다.
- 이후 검증 예외가 발생하면 같은 트랜잭션이 롤백되므로 앞의 상태 변경도 롤백된다.

### 15.2 `DIRECT` 나가기

- 퇴장 메시지를 저장하지 않는다.
- 상대방의 상태, 역할, 인원수 정보를 변경하지 않는다.
- 상대방에게 `ROOM_CHANGED`를 보내지 않는다.
- 요청자에게만 `ROOM_REMOVED`를 보낸다.
- 메시지 표시·읽음 커서는 그대로 남고, 나중에 재입장할 때 새 경계로 덮어쓴다.

### 15.3 `GROUP` 나가기와 방장 양도

1. 요청자를 제외한 삭제되지 않은 `ACTIVE` 참여자를 조회한다.
2. 요청자가 `OWNER`이고 다른 활성 참여자가 남아 있으면 `nextOwnerId`가 그 목록에 반드시 있어야 한다.
3. 유효하지 않거나 빠졌으면 `CR011`이다.
4. 유효하면 기존 방장을 `MEMBER`, 대상 참여자를 `OWNER`로 변경한다.
5. 요청자가 일반 멤버면 `nextOwnerId`는 사용하지 않는다.
6. 요청자가 방장이어도 다른 활성 참여자가 0명이면 양도 없이 `LEFT`가 된다. 이때 역할 값은 별도로 변경하지 않는다.

### 15.4 퇴장 메시지와 이벤트

1. 남은 인원 유무와 관계없이 요청자 발신의 `LEAVE_TEXT`를 저장한다.
2. content는 `{username}님이 채팅방에서 나가셨습니다.`다.
3. 각 남은 활성 참여자에게 `ROOM_CHANGED`를 만든다.
4. `memberCount`는 남은 활성 참여자 수다.
5. 각 수신자의 preview는 남은 사람 중 자신을 제외한 이름순 최대 4명이다.
6. 양도 대상의 이벤트에는 변경된 `myRole = OWNER`가 들어간다.
7. 요청자에게는 별도 `ROOM_REMOVED`를 추가한다.
8. 목록 이벤트를 먼저 발행한다.
9. LEAVE_TEXT의 내부 `eventUserIds`는 비어 있어 방 topic 이벤트만 발행되고 자동 목록 `MESSAGE_SENT`는 없다.
10. 남은 참여자가 0명이면 ROOM_CHANGED 수신자는 없고 요청자의 ROOM_REMOVED와 topic 이벤트만 생성된다.

### 15.5 모든 참여자가 나간 GROUP의 수명주기

- 마지막 활성 참여자도 나가면 `ChatRoom`과 `ChatRoomUser`, 저장된 메시지, 마지막 `LEAVE_TEXT`는 DB에 남는다. 채팅방 삭제는 수행하지 않는다.
- GROUP에는 DIRECT 생성처럼 기존 방을 찾아 재입장시키는 API가 없다. 초대 API도 호출자가 해당 방의 삭제되지 않은 `ACTIVE` 참여자여야 하므로 모든 참여자가 `LEFT`인 GROUP은 현재 공개 API로 복구할 수 없다.

### 15.6 GROUP 방장 회원 삭제

- `DELETE /me`는 `User.deleted=true`와 Redis 세션 제거만 수행하고 `ChatRoomUser.status`·`role`은 바꾸지 않는다. 정상 `/leave`의 방장 양도 로직을 호출하지 않는다.
- GROUP OWNER가 회원 삭제되면 삭제 사용자의 `ACTIVE/OWNER` 행은 남지만 활성 참여자 조회와 목록 쿼리에서는 제외된다. 남은 사용자가 모두 `MEMBER`이면 공통 이름 변경은 `CR019`로 실패하며 자동으로 새 OWNER를 정하는 서버 흐름이 없다.
- 회원 삭제에 따른 `ROOM_CHANGED`·`ROOM_REMOVED` 또는 방 topic 이벤트도 발행하지 않는다.

## 16. 채팅방 이름 변경

### 16.1 공통 이름

- endpoint: `PATCH /chatRooms/{chatRoomId}/name`
- body: `{ "roomName": "..." }`
- `@NotBlank`, 최대 100자 검증을 통과해야 한다. 실패하면 `C001`이다.
- 저장 전 `strip()`으로 앞뒤 공백을 제거한다.
- 삭제되지 않은 `ACTIVE` 참여자여야 한다.
- `GROUP`에서만 가능하다. DIRECT면 `CR018`이다.
- 요청자가 `OWNER`여야 한다. 아니면 `CR019`다.
- 모든 삭제되지 않은 `ACTIVE` 참여자에게 `ROOM_NAME_CHANGED`를 보낸다.
- 각 이벤트에는 같은 새 `baseRoomName`과 그 수신자 개인의 `customRoomName`을 함께 넣는다.
- 메시지나 방 topic 이벤트는 만들지 않는다.

### 16.2 개인 이름

- endpoint: `PATCH /chatRooms/{chatRoomId}/customName`
- body: `{ "customRoomName": "..." }`
- `@NotBlank`, 최대 100자 검증 후 `strip()`한다.
- 삭제되지 않은 `ACTIVE` 참여자면 DIRECT/GROUP 모두 변경할 수 있다.
- 요청자의 `ChatRoomUser.customRoomName`만 변경한다.
- 요청자에게만 `ROOM_NAME_CHANGED`를 보낸다.
- blank가 금지되므로 이 API로 개인 이름을 `null`로 초기화할 수 없다.
- 채팅방을 나가면 `leaveChatRoom()`이 자동으로 개인 이름을 `null`로 만든다.

서버는 최종 표시 이름 문자열을 조합하지 않고 `customRoomName`, `baseRoomName`, `previewUsers`를 분리해 제공한다.

## 17. 초대 가능 친구 조회

- endpoint: `GET /chatRooms/{chatRoomId}/invitableFriends`
- 요청자는 삭제되지 않은 `ACTIVE` 참여자여야 한다.
- 요청자의 친구 중 삭제되지 않은 사용자만 후보가 된다.
- `DIRECT`에서는 그 방에 `ChatRoomUser` 이력이 있는 상대를 `ACTIVE/LEFT`와 관계없이 제외한다.
- `GROUP`에서는 현재 `ACTIVE` 참여자를 제외한다.
- `GROUP`의 `LEFT` 참여자는 복귀 가능하므로 후보에 포함한다.
- 결과는 Java 문자열 기준 사용자 이름 오름차순이다.
- 응답 필드는 `userId`, `username`, `profileImageKey`다.

이 조회는 UI 보조 API다. 실제 초대 시 서비스가 친구·삭제·참여 상태를 다시 검증하므로 조회 후 상태가 바뀐 요청은 실패할 수 있다.

## 18. 참여자 상세 조회

- endpoint: `GET /chatRooms/{chatRoomId}/members`
- 요청자는 삭제되지 않은 `ACTIVE` 참여자여야 한다.
- 삭제된 사용자는 결과에서 제외한다.
- `DIRECT`는 `ACTIVE/LEFT`에 관계없이 삭제되지 않은 기존 두 참여자를 표시 대상으로 한다.
- `GROUP`은 현재 `ACTIVE` 참여자만 표시한다.
- 결과는 이름 오름차순이다.
- 응답 필드는 `userId`, `username`, `profileImageKey`, `chatRoomUserRole`, `canAddFriend`다.
- `canAddFriend`는 자기 자신이거나 이미 현재 친구면 `false`, 그 외면 `true`다.
- 응답에는 `ChatRoomUserStatus`가 없으므로 DIRECT 응답만으로 상대방이 `LEFT`인지 구분할 수 없다.

## 19. 채팅방 목록 조회

### 19.1 기본 흐름

- endpoint: `GET /chatRooms`
- 요청 사용자가 존재하고 삭제되지 않았는지 먼저 검사한다.
- 요청자의 `ChatRoomUserStatus = ACTIVE`인 방만 기본 목록에 포함한다.
- 기본 행이 없으면 추가 쿼리를 실행하지 않고 빈 배열을 반환한다.
- 목록 자체의 페이지네이션은 없다.

서비스는 MyBatis 결과 다섯 종류를 `roomId`로 조합한다.

### 19.2 기본 정보 조회

`findActiveChatRoomsByUserId()`가 다음 값을 가져온다.

- `roomId`
- `roomType`
- `baseRoomName`
- 요청자의 `customRoomName`
- 요청자의 `myRole`
- 요청자의 `joinedAt`
- 요청자의 `unreadStartMessageId`

### 19.3 인원수

- 삭제된 사용자는 제외한다.
- `DIRECT`는 `ACTIVE/LEFT`에 관계없이 삭제되지 않은 참여자를 센다.
- `GROUP`은 삭제되지 않은 `ACTIVE` 참여자만 센다.
- 따라서 삭제된 DIRECT 상대방이 있으면 인원수가 2보다 작을 수 있다.

### 19.4 미리보기 사용자

- 요청자 자신과 삭제된 사용자를 제외한다.
- 방별 사용자 이름순 최대 4명이다.
- 정렬은 MySQL 쿼리의 `ORDER BY username`과 DB collation을 따른다.
- 생성·초대·퇴장 목록 이벤트의 preview는 Java `String.compareTo()`로 정렬한다. 대소문자나 비ASCII 이름에서는 DB collation과 순서가 달라져 HTTP 목록과 이벤트의 상위 4명 구성이 다를 수 있다.
- `DIRECT`는 `ACTIVE/LEFT`에 관계없이 상대방을 포함한다.
- `GROUP`은 `ACTIVE` 사용자만 포함한다.

### 19.5 최근 표시 가능 메시지

- 요청자의 `visibleStartMessageId` 이상인 메시지만 후보로 한다.
- 방별 `chatMessageId` 내림차순 첫 메시지를 선택한다.
- TEXT, FILE, JOIN_TEXT, LEAVE_TEXT를 모두 동일하게 최근 메시지 후보로 본다.
- 목록 응답에는 최근 메시지 content, message ID, 생성 시각만 담고 발신자나 파일 목록은 담지 않는다.
- 표시 가능한 메시지가 없으면 최근 내용과 message ID는 `null`이다.

### 19.6 안 읽은 메시지 개수

- 방별로 `chatMessageId >= 요청자의 unreadStartMessageId`인 모든 메시지를 센다.
- 메시지 타입과 발신자를 구분하지 않으므로 자기 메시지와 시스템 메시지도 포함된다.
- 해당 메시지가 없으면 서비스 조합 단계에서 `0`을 사용한다.

### 19.7 최종 조합과 정렬

- 최근 메시지가 있으면 그 `createdAt`을 `lastActivityAt`으로 사용한다.
- 최근 표시 가능 메시지가 없으면 요청자의 `joinedAt`을 사용한다.
- 결과는 `lastActivityAt` 내림차순이다.
- 동률일 때 사용할 2차 정렬 키는 없다.
- 응답 필드는 다음과 같다.
  - `roomId`, `roomType`
  - `baseRoomName`, `customRoomName`
  - `myRole`
  - `memberCount`, `previewUsers`
  - `lastMessagePreview`, `messageId`, `lastActivityAt`
  - `unreadStartMessageId`, `unreadCount`

## 20. 파일 바이너리 조회

### 20.1 요청

- endpoint: `GET /media/messages/{chatMessageId}/files/{fileOrder}`
- query parameter `storedFileVariant`는 enum `ORIGINAL` 또는 `THUMBNAIL`이다.
- 인증값은 HttpOnly `mediaToken` 쿠키다.
- mediaToken은 로그인·재발급 시 발급되며 현재 JWT TTL은 10분이다.

### 20.2 권한 검사

1. mediaToken의 서명, 만료, 형식을 파싱한다.
2. `type=media`가 아니면 `J009`다.
3. 토큰 `sub`의 사용자를 조회하고 삭제 상태를 검사한다. 실패하면 `U003`이다.
4. `chatMessageId`의 메시지와 방을 조회한다. 없으면 `CM001`이다.
5. 사용자의 해당 방 `ChatRoomUser`가 없거나 `LEFT`면 `CR010`이다.
6. 메시지 ID가 사용자의 `visibleStartMessageId`보다 작으면 `CR010`이다.
7. `fileKey`, `fileOrder`, variant가 정확히 일치하는 `StoredFile`을 조회한다. 없으면 `SF002`다.
8. 로컬 경로가 일반 파일로 실제 존재하는지 검사한다. 없으면 `SF002`다.

- 메시지 타입이 `FILE`인지 별도 검사하지 않는다. 잘못된 메시지 ID는 연결된 StoredFile이 없으므로 최종적으로 `SF002`가 된다.
- 일반 파일에는 썸네일 행이 없으므로 `THUMBNAIL` 요청은 `SF002`다.
- mediaToken도 Redis 세션과 대조하지 않는다.
- 별도의 공개 프로필 조회 경로는 이 권한 검사를 공유하지 않는다. `/profile-images/**`는 GET whitelist이고 서비스가 `user:` prefix나 파일 용도를 확인하지 않아, 단일 파일 메시지의 `chat-message:{messageId}`를 프로필 원본/썸네일 경로에 넣으면 현재 코드상 mediaToken·멤버십 검사 없이 일치하는 파일을 받을 수 있다. 같은 key에 원본 또는 썸네일 행이 여러 개면 단건 Repository 조회가 복수 결과 오류로 끝날 수 있다. 이는 보호 파일 API의 보안 우회 경계이며 상세는 [FILEFLOW.md](./FILEFLOW.md)를 참고한다.

### 20.3 HTTP 응답

- `FileCategory.FILE`은 `application/octet-stream`과 attachment `Content-Disposition`을 사용한다.
- IMAGE/VIDEO는 저장된 content type을 사용한다.
- IMAGE/VIDEO 응답은 private 10분 cache-control과 `Vary: Cookie`를 설정한다.
- IMAGE/VIDEO의 `ORIGINAL`은 원래 파일명으로 inline `Content-Disposition`을 설정한다.
- 썸네일 응답에는 별도의 Content-Disposition을 설정하지 않는다.

## 21. 예외 처리

### 21.1 주요 도메인 오류

| 코드 | HTTP status | 의미 |
|---|---:|---|
| `C001` | 400 | REST Bean Validation 실패 |
| `U003` | 404 | 사용자 없음 또는 삭제 상태 |
| `CR002` | 400 | 자기 자신과 방 생성 불가 |
| `CR003` | 400 | GROUP 생성 대상 없음 |
| `CR004` | 400 | DIRECT/GROUP 생성 대상이 유효한 친구가 아님 |
| `CR005` | 400 | 초대 대상이 유효한 친구가 아님 |
| `CR006` | 400 | 초대 대상 없음 |
| `CR007` | 400 | 자기 자신 초대 |
| `CR010` | 403 | 방 참여 행 없음, LEFT 상태 등 방 접근 거부 |
| `CR011` | 400 | GROUP 방장 양도 대상 오류 |
| `CR013` | 400 | DIRECT 전환 요청에 신규 사용자 없음 |
| `CR014` | 400 | DIRECT 전용 초대 API 필요 |
| `CR015` | 400 | GROUP 전용 초대 API 필요 |
| `CR016` | 400 | GROUP 초대 대상이 모두 이미 ACTIVE |
| `CR017` | 400 | 삭제된 DIRECT 기존 참여자로 인해 GROUP 전환 불가 |
| `CR018` | 400 | DIRECT 공통 이름 변경 불가 |
| `CR019` | 403 | 공통 이름 변경 방장 권한 없음 |
| `CM001` | 404 | 해당 방 메시지 또는 조회 대상 메시지 없음 |
| `CM002` | 403 | 가시성 경계 이전 메시지 읽음 요청 |
| `SF001` | 400 | 지원하지 않는 이미지 형식 |
| `SF002` | 404 | 파일 메타데이터 또는 실제 파일 없음 |
| `SF003` | 400 | 유효한 파일 없음 |
| `SF004` | 400 | 30개 파일 제한 초과 |
| `SF005` | 413 | 전체 파일 크기 제한 초과 |
| `W001` | 401 | Principal 없는 WebSocket 요청 |
| `S001` | 500 | 처리되지 않은 서버 예외 |

- `CR001 CHAT_ROOM_NOT_EXISTS`, `CR008 CHAT_ROOM_INVITE_PERMISSION_DENIED`, `CR0012 CHAT_PARTNER_DELETED`는 `ErrorCode`에 선언되어 있지만 현재 main 코드에서 발생시키는 곳이 없다. 특히 실제 문자열도 `CR0012`이며 `CR012`가 아니다.

### 21.2 REST 오류

- `ErrorException`은 `GlobalExceptionHandler`가 ErrorCode의 HTTP status와 `ErrorResponse`로 변환한다.
- `@Valid` 실패는 `C001`이고 첫 번째 field error 메시지를 응답 message로 사용한다.
- 그 밖의 처리되지 않은 예외는 `S001`이다.
- 공통 오류 body 필드는 `code`, `status`, `message`, `timestamp`다.

### 21.3 STOMP 오류

- CONNECT 처리 실패는 `StompErrorHandler`가 STOMP `ERROR` 프레임으로 변환한다. 연결 수립 단계 오류이므로 클라이언트는 연결 실패로 처리해야 한다.
- 인증된 사용자의 방 topic 구독 권한 실패는 연결 전체를 끊지 않고 해당 SUBSCRIBE만 차단하며 `/user/queue/errors`로 보낸다.
- `@MessageMapping` 안의 `ErrorException`은 `StompMessageExceptionAdvice`가 `/user/queue/errors`로 보낸다.
- handler 오류의 `@SendToUser(broadcast = false)`는 오류를 발생시킨 현재 세션으로 한정한다.
- 처리되지 않은 STOMP handler 예외는 `S001`로 변환한다.
- RedisSubscriber 내부 오류는 사용자 error queue로 보내지 않고 서버 로그에만 남긴다.

## 22. 트랜잭션, 순서, 정합성

### 22.1 DB 트랜잭션 범위

- 채팅방 생성·초대·나가기·이름 변경, 메시지 저장·읽음, 파일 메시지 저장 서비스는 `@Transactional`이다.
- 채팅방/메시지/읽음 목록 조회는 `readOnly = true`다. 파일 바이너리 조회 메서드 자체에는 트랜잭션 어노테이션이 없다.
- 이벤트 객체는 트랜잭션 안에서 만들지만 Redis 발행은 서비스가 반환된 뒤 컨트롤러에서 수행한다.
- 일반적인 성공 경로에서는 DB commit 이후 Redis 발행이 실행된다.

### 22.2 DB와 이벤트의 비원자성

- DB transaction과 Redis publish를 하나로 묶는 outbox가 없다.
- DB commit 후 Redis 발행이 실패하면 DB 상태는 이미 변경됐지만 HTTP/STOMP 요청은 오류로 보이거나 이벤트가 누락될 수 있다.
- Redis 발행 성공 후 클라이언트가 이벤트를 받았다는 ACK도 저장하지 않는다.
- 요청 idempotency key가 없으므로 클라이언트 재시도는 방 생성의 유니크 제약 같은 일부 예외를 제외하면 같은 작업을 다시 실행할 수 있다.
- 실시간 이벤트는 변경 알림이며 최종 복구 기준은 HTTP 조회 결과다.

### 22.3 메시지 순서

- 서버의 영속 정렬 기준은 `chatMessageId`다.
- Redis/STOMP 수신 순서만으로 최종 메시지 순서를 확정하지 않는다.
- 서로 다른 방의 ID가 중간에 끼므로 한 방 안의 ID가 연속적이지 않아도 정상이다.
- 같은 메시지 ID를 topic 재수신하거나 HTTP 조회와 중복해서 얻을 수 있으므로 ID 기반 병합이 안전하다.

### 22.4 로컬 파일과 다중 인스턴스

- 파일 바이너리는 `fileRootPath` 아래 로컬 경로에 저장하고 Redis는 이벤트만 전달한다.
- 다중 애플리케이션 인스턴스가 같은 공유 볼륨을 보지 않는 상태에서 파일 조회 요청의 affinity도 보장되지 않으면, 업로드와 다른 인스턴스가 DB 메타데이터만 조회한 뒤 물리 파일을 찾지 못해 `SF002`를 반환할 수 있다. 공유 저장소 또는 확실한 요청 affinity 중 하나가 이 문제를 막는 조건이다.
- 현재 Compose는 단일 app 인스턴스와 host bind mount를 사용한다. 확장 시 저장소 공유는 별도 운영 제약이다.

### 22.5 현재 자동화 검증 범위

- 현재 `src/test/java`에는 chat domain, messaging, file domain 전용 자동화 테스트가 없다.
- 따라서 이 문서의 채팅 흐름은 현재 구현 코드와 쿼리를 직접 대조한 결과이며, 채팅 고위험 변경 시에는 서비스·STOMP interceptor·Redis subscriber·MyBatis 조회에 대한 회귀 테스트를 추가하는 것이 필요하다.

## 23. 프론트엔드 책임

이 절은 서버가 강제하는 규칙이 아니라, 위 서버 계약을 안전하게 소비하기 위해 프론트가 담당해야 할 상태 관리다.

### 23.1 로그인 후 연결

1. STOMP CONNECT native header에 `Authorization: Bearer {accessToken}`을 넣는다.
2. `/user/queue/chatRooms/list`, `/user/queue/users/metadata`, `/user/queue/errors`를 구독한다.
3. 채팅방 화면 진입 시 `/topic/chatRooms/{roomId}`를 구독한다.
4. 방 화면 이탈 또는 `ROOM_REMOVED` 수신 시 해당 topic을 즉시 UNSUBSCRIBE한다.
5. AccessToken 만료·갱신 후에는 기존 연결이 프레임마다 토큰을 재검증하지 않는다는 점에 의존하지 말고 새 토큰으로 연결을 재수립한다.

- SockJS fallback을 사용한다면 POST transport가 현재 GET 전용 `/ws/**` whitelist를 통과하지 못할 수 있다. 배포에서 실제 선택되는 transport를 확인하고, 프론트의 STOMP CONNECT header만으로 모든 SockJS HTTP 요청이 인증된다고 가정하지 않는다.

### 23.2 기준 상태와 재연결

- 최초 로그인/연결 시 `GET /chatRooms` 결과를 목록 기준 상태로 사용한다.
- 방 진입 시 메시지 HTTP 첫 페이지와 `readStatuses`를 기준 상태로 사용한다.
- Redis Pub/Sub에는 재생 기능이 없으므로 연결이 끊겼다가 복구되면 목록과 현재 방 메시지를 HTTP로 다시 동기화한다.
- HTTP 초기 조회와 WebSocket 구독 사이의 이벤트 유실 구간을 줄이려면 먼저 구독하고, 조회 중 받은 이벤트를 임시 저장한 뒤 HTTP 기준 상태 위에 ID 순으로 적용한다.
- DB commit과 Redis 이벤트는 원자적이지 않으므로 이벤트 오류 후에도 HTTP 재조회에서 상태 변경이 보일 수 있다.

### 23.3 메시지 상태

- 메시지는 `messageId`를 키로 저장하고 중복 이벤트·HTTP 응답을 upsert한다.
- 화면 표시 순서는 `messageId` 오름차순으로 구성한다. HTTP 응답은 내림차순이므로 그대로 append하지 않는다.
- 이전 페이지는 현재 가장 작은 message ID를 `offsetMessageId`로 요청한다.
- 응답 길이가 100보다 작으면 더 이전 페이지가 없다고 판단할 수 있지만 서버가 별도 `hasNext`를 주지는 않는다.
- topic 이벤트 도착 순서가 뒤섞일 수 있으므로 현재 마지막 ID보다 작다는 이유만으로 무조건 버리지 말고 ID 위치에 병합한다.
- 발신자 필드가 모두 `null`이면 삭제된 사용자의 과거 메시지로 표시한다.
- `JOIN_TEXT`, `LEAVE_TEXT`는 일반 사용자 입력과 구분해 시스템 메시지 UI로 표시한다.

### 23.4 읽음 상태

- 사용자별 `unreadStartMessageId`는 감소시키지 않는다.
- topic `MESSAGE_READ`도 기존 값보다 큰 커서만 적용한다.
- 목록의 `MESSAGE_READ`는 서버가 계산한 `unreadCount`와 커서를 함께 주므로 해당 방의 로컬 값을 교체한다.
- `MESSAGE_SENT`와 `ROOM_CHANGED`에는 정확한 새 unreadCount가 없으므로 필요한 경우 로컬에서 계산하되 재접속·경합 후에는 `GET /chatRooms`로 보정한다.
- 자기 메시지와 JOIN/LEAVE 메시지도 서버 DB unread count에 포함된다.
- 방에서 실제로 확인한 가장 큰 표시 가능 message ID를 `/app/chatRooms/{roomId}/read`로 보낸다.
- 이전 read 요청 응답이 늦게 와도 더 큰 커서를 유지한다.

### 23.5 채팅방 목록 이벤트 적용

| 이벤트 | 프론트 처리 |
|---|---|
| `ROOM_ADDED` | `roomId` 기준 전체 항목 upsert. 타입상 의미 있는 필드는 `null`도 서버 상태로 적용 |
| `ROOM_CHANGED` | 인원, 미리보기, 역할, 방 타입 등 구조 필드를 적용하고 최근 메시지 부분은 ID를 비교해 갱신 |
| `ROOM_NAME_CHANGED` | 기본 이름과 개인 이름 갱신 |
| `ROOM_REMOVED` | 목록에서 제거하고 열린 화면/topic 구독 종료 |
| `MESSAGE_SENT` | 더 큰 `messageId`일 때 최근 메시지와 활동 시각 갱신 후 재정렬 |
| `MESSAGE_READ` | 커서와 unread count 갱신 |

- 이벤트 DTO의 `null`에는 “이 이벤트에서 사용하지 않음”과 “도메인의 실제 null 값” 두 의미가 있다. 이벤트 타입 계약에서 사용하지 않는 필드는 무시하되, 그 타입에 의미 있는 `baseRoomName`, `customRoomName`, 최근 메시지 값 등은 `null`도 서버 상태일 수 있다.
- `ROOM_ADDED`·`ROOM_CHANGED`·`MESSAGE_SENT`가 가진 최근 메시지 부분은 로컬 `messageId`와 비교해 더 최신일 때 갱신한다. 인원·역할·이름 같은 구조 필드는 최근 메시지 비교와 분리해 적용한다.
- `roomId`를 목록의 안정적인 키로 사용한다.
- `lastActivityAt`이 바뀌면 목록을 다시 내림차순 정렬한다.
- 동일한 작업에서 목록 이벤트와 topic 이벤트 중 어느 것이 먼저 올지 가정하지 않는다.
- 서버가 최종 표시 이름을 주지 않으므로 화면 정책을 한 곳에 고정한다. 현재 데이터 구조상 보통 `customRoomName` 우선, 없으면 `baseRoomName`, 둘 다 없으면 `previewUsers` 이름 조합을 사용한다.

### 23.6 파일 메시지

- `chatEventFiles`의 `fileOrder`를 안정적인 파일 키로 사용한다.
- 원본/썸네일 URL은 `/media/messages/{messageId}/files/{fileOrder}?storedFileVariant=ORIGINAL|THUMBNAIL`로 만든다.
- mediaToken은 HttpOnly 쿠키이므로 JavaScript에서 값을 읽어 URL에 붙이지 않는다. 브라우저가 쿠키를 보내도록 요청 credential 설정과 배포 origin/cookie 정책을 맞춘다.
- IMAGE와 VIDEO는 먼저 `THUMBNAIL`을 표시하고 필요 시 `ORIGINAL`을 요청할 수 있다.
- 일반 FILE은 `THUMBNAIL`이 없으므로 바로 `ORIGINAL`을 다운로드한다.
- mediaToken 만료 오류가 발생하면 인증 재발급 흐름을 수행한 뒤 다시 요청한다.
- 파일 메시지의 목록 preview는 실제 파일명이 아니라 서버가 저장한 `파일 N개`다.

### 23.7 사용자 이름과 프로필 변경

- `/user/queue/users/metadata`의 사용자 ID를 기준으로 채팅방 `previewUsers`, 현재 표시 중인 사용자 카드, 필요하면 캐시된 발신자 표시 정보를 갱신한다.
- 과거 메시지 응답 자체의 발신자 문자열이 서버에서 자동 재전송되지는 않는다.
- 프로필 이미지 키가 바뀌면 기존 이미지 URL 캐시 키도 함께 교체한다.
- GROUP 이벤트가 Java `String.compareTo()`로 만든 preview 순서와 HTTP 목록이 DB collation의 `ORDER BY username`으로 만든 순서는 대소문자·비ASCII 이름에서 다를 수 있다.
- 메타데이터 이벤트에는 room ID나 재계산된 preview 목록이 없다. 이름 변경으로 상위 4명 구성이 달라질 수 있는 화면은 `GET /chatRooms`를 다시 조회하거나 전체 멤버 상태로 직접 재계산해야 서버 목록과 정확히 맞출 수 있다.

### 23.8 오류와 재시도

- STOMP CONNECT `ERROR`는 연결 실패로 처리하고 토큰 갱신 또는 로그인으로 전환한다.
- `/user/queue/errors` 오류는 연결 전체 실패가 아니라 해당 SEND/SUBSCRIBE 작업 실패로 처리한다.
- 쓰기 요청이 `S001` 또는 네트워크 오류로 끝났더라도 DB commit 후 이벤트만 실패했을 가능성이 있다.
- 같은 쓰기를 즉시 무조건 재전송하기 전에 HTTP 목록·메시지 조회로 실제 반영 여부를 확인한다.
- 초대 가능 친구 목록과 참여자 목록은 실시간 권한의 보장이 아니므로 서버의 최종 `CRxxx` 응답을 기준으로 UI를 갱신한다.

## 24. 관련 코드 위치

- REST/STOMP controller: `domain/chat/controller/**`, `domain/file/controller/StoredFileController`
- 채팅 유스케이스: `domain/chat/service/ChatRoomService`, `ChatMessageService`
- 파일 메시지: `domain/file/service/StoredFileService`
- 영속 모델: `domain/chat/entity/**`, `domain/file/entity/StoredFile`
- JPA repository: `domain/chat/repository/**`, `domain/file/repository/StoredFileRepository`
- MyBatis 목록/파일 조회: `resources/mappers/chat/ChatRoomMapper.xml`, `resources/mappers/file/StoredFileMapper.xml`
- STOMP 설정·인증·구독 권한·오류: `common/messaging/config/WebSocketConfig`, `common/messaging/stomp/**`
- Redis 발행·구독: `common/messaging/redis/**`, `common/messaging/config/RedisConfig`
- 이벤트 계약: `common/messaging/event/**`
- HTTP 인증 연계: `common/security/**`, 상세 인증 흐름은 `docs/AUTHFLOW.md`
- 파일 저장·조회 전체 흐름: [FILEFLOW.md](./FILEFLOW.md)
