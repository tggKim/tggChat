# CHATFLOW.md

## 목적
- 채팅 도메인의 현재 WebSocket 설정과 STOMP 인증, 구독 권한 검증, 메시지 목록 조회 흐름을 정리한다.
- 채팅방 멤버십 모델은 현재 단계적으로 전환 중이므로, 아직 확정되지 않은 나가기, 재입장 정책은 이 문서에 고정하지 않는다.

## 현재 전환 상태
- 기존 채팅방 멤버십 로직에는 `ChatRoomUserStatus.ACTIVE`, `ChatRoomUserStatus.LEFT` 기반 흐름이 남아 있다.
- 최종적으로는 `ChatRoomUser`를 현재 채팅방 멤버만 표현하는 방향으로 단순화할 예정이지만, 관련 로직을 한 번에 제거하지 않는다.
- WebSocket 구독 권한 검증과 메시지 목록 조회부터 단계적으로 정리하고, 이후 채팅방 목록, 나가기, 재입장 흐름은 별도 판단 후 수정한다.
- 따라서 `chatRoomUserStatus` 필드와 `ChatRoomUserStatus` enum은 모든 의존 로직이 정리된 뒤 마지막에 제거한다.

## WebSocket 설정
- WebSocket 연결 엔드포인트는 `/ws`다.
- 클라이언트가 서버로 메시지를 보낼 때는 `/app` prefix를 사용한다.
- 클라이언트가 브로드캐스트 메시지를 구독할 때는 `/topic` 또는 `/queue` prefix를 사용한다.
- 유저별 메시지 라우팅은 `/user` prefix를 사용한다.
- 현재 inbound channel에는 `JwtChannelInterceptor`와 `ChatRoomSubscriptionInterceptor`가 등록되어 있다.

## STOMP 인증 흐름
- `JwtChannelInterceptor`는 STOMP `CONNECT` 요청에서 AccessToken을 검증한다.
- AccessToken 검증에 성공하면 로그인 유저 ID를 기반으로 `StompPrincipal`을 생성한다.
- 생성된 Principal은 STOMP 세션에 저장된다.
- 이 Principal은 이후 `@MessageMapping`에서 현재 유저를 식별하거나 `/user/**` 라우팅에 사용된다.

## 채팅방 구독 권한 검증
- `ChatRoomSubscriptionInterceptor`는 STOMP `SUBSCRIBE` 요청을 검사한다.
- 검증 대상 경로는 `/topic/chatRooms/{chatRoomId}` 형식의 채팅방 메시지 구독 경로다.
- 해당 경로가 아닌 구독 요청은 별도 권한 검증 없이 통과시킨다.
- 채팅방 구독 요청이면 STOMP 세션의 Principal에서 로그인 유저 ID를 추출한다.
- Principal이 없으면 인증되지 않은 WebSocket 요청으로 처리한다.
- Principal이 있으면 `ChatRoomSubscriptionService`에 구독 가능 여부 검증을 위임한다.

## 메시지 송수신 흐름
- 클라이언트는 STOMP `CONNECT` 요청으로 WebSocket 연결을 생성한다.
- 연결 성공 후 채팅방 메시지를 실시간으로 받기 위해 `/topic/chatRooms/{chatRoomId}` 경로를 구독한다.
- 채팅방 목록 갱신 이벤트를 받기 위해 `/user/queue/chatRooms/list` 경로를 구독한다.
- 클라이언트가 메시지를 보낼 때는 `/app/chatRooms/{chatRoomId}/message` 경로로 `SEND` 요청을 보낸다.
- `ChatMessageStompController`는 STOMP 세션의 `Principal`에서 로그인 유저 ID를 추출한다.
- `ChatMessageService.saveMessage()`는 요청 유저의 `ChatRoomUser`, `ChatRoom`, `User`를 함께 조회한다.
- 요청 유저가 채팅방에서 나간 상태이거나 삭제된 유저이면 메시지 저장을 차단한다.
- 메시지를 `chat_message` 테이블에 먼저 저장하고, 데이터베이스가 생성한 `chatMessageId`를 메시지 식별자와 정렬 기준으로 사용한다.
- 1대1 채팅방의 경우 삭제되지 않은 상대 유저가 `LEFT` 상태이면 다시 `ACTIVE` 상태로 복귀시킨다.
- 복귀한 상대 유저의 `visibleStartMessageId`와 `unreadStartMessageId`는 방금 저장된 메시지의 `chatMessageId`로 설정한다.
- 따라서 복귀한 상대 유저는 복귀를 발생시킨 메시지부터 조회하고 읽지 않은 메시지로 판단한다.
- 복귀한 상대 유저에게 보낼 `ROOM_ADDED` 타입의 `ChatRoomListEvent` 생성한다.
- 1대1 상대 유저가 삭제된 상태이면 상대 복귀와 목록 이벤트 생성 없이 메시지만 저장한다.
- 단체 채팅방의 경우 현재 `ACTIVE` 상태이고 삭제되지 않은 유저만 `ChatEvent` 수신 대상에 포함한다.
- `ChatMessageService.saveMessage()`는 `ChatEvent`와 `ChatRoomListEvent`를 함께 담은 결과를 반환한다.
- `ChatMessageStompController`는 채팅방 목록 이벤트를 먼저 Redis Pub/Sub로 발행하고, 이후 채팅 메시지 이벤트를 발행한다.
- `RedisPublisher`는 `ChatEvent`를 `chat:room:{roomId}` 채널로 발행한다.
- `RedisPublisher`는 `ChatRoomListEvent`를 `chat:room-list` 채널로 발행한다.
- `RedisSubscriber`는 `chat:room:*` 채널의 `ChatEvent`를 수신해 `/topic/chatRooms/{roomId}`로 브로드캐스트한다.
- `RedisSubscriber`는 동일한 `ChatEvent`를 기반으로 `MESSAGE_SENT` 타입의 `ChatRoomListEvent`를 만들어 `eventUserIds`의 `/user/queue/chatRooms/list` 경로로 전달한다.
- `RedisSubscriber`는 `chat:room-list` 채널의 `ChatRoomListEvent` 목록을 수신해 각 `receiverUserId`의 `/user/queue/chatRooms/list` 경로로 전달한다.

## 메시지 목록 조회 흐름
- 대상 엔드포인트는 `GET /chatRooms/{chatRoomId}/messages`다.
- `ChatMessageController.findChatMessages()`는 인증된 사용자의 ID, `chatRoomId`, `offsetMessageId`를 `ChatMessageService`에 전달한다.
- `ChatMessageService.findChatMessages()`는 요청한 사용자가 해당 채팅방에 속해 있는지 확인한다.
- 요청한 사용자가 `LEFT` 상태이면 `CHAT_ROOM_ACCESS_DENIED` 예외를 발생시킨다.
- 요청한 사용자가 삭제된 사용자이면 `USER_NOT_FOUND` 예외를 발생시킨다.
- 접근 검증에 성공하면 요청한 사용자의 `ChatRoomUser.visibleStartMessageId`를 기준으로 조회 가능한 메시지를 조회한다.
- 메시지 ID가 `visibleStartMessageId`보다 크거나 같은 메시지만 조회하므로, 경계값에 해당하는 메시지부터 사용자에게 노출된다.
- 최초 조회에서는 `offsetMessageId`를 전달하지 않으며 최신 메시지부터 최대 100개를 조회한다.
- 이전 메시지를 추가로 조회할 때는 현재 목록에서 가장 작은 `messageId`를 `offsetMessageId`로 전달한다.
- 추가 조회에서는 `messageId < offsetMessageId` 조건을 적용하여 이미 조회한 메시지를 제외한다.
- 조회 결과는 `messageId` 내림차순으로 정렬한다.
- 메시지와 발신자를 `fetch join`하여 한 번의 쿼리로 조회한다.
- 메시지별 읽음 여부와 안 읽은 사용자 수는 별도로 조회한 유저별 `unreadStartMessageId`를 기준으로 클라이언트가 계산한다.

## 채팅방 유저별 메시지 읽음 범위 조회 흐름
- `ChatRoomController.findReadStatuses()`는 인증된 사용자의 ID와 `chatRoomId`를 `ChatRoomService`에 전달한다.
- `ChatRoomService.findReadStatuses()`는 요청한 사용자가 해당 채팅방에 속해 있는지 확인한다.
- 요청한 사용자가 `LEFT` 상태이면 `CHAT_ROOM_ACCESS_DENIED` 예외를 발생시킨다.
- 요청한 사용자가 삭제된 사용자이면 `USER_NOT_FOUND` 예외를 발생시킨다.
- 접근 검증에 성공하면 해당 채팅방의 `ACTIVE` 상태이며 삭제되지 않은 유저를 조회한다.
- 각 유저의 `userId`와 `unreadStartMessageId`를 응답한다.
- `unreadStartMessageId`는 해당 ID의 메시지를 포함하여 이후 메시지를 읽지 않았다는 것을 나타내는 경계값이다.
- 따라서 메시지 ID가 `unreadStartMessageId`보다 작으면 읽은 메시지이고, 크거나 같으면 읽지 않은 메시지로 판단한다.

## 1대1 채팅방 생성 흐름
- 대상 엔드포인트는 `POST /directChatRooms`다.
- 요청 사용자가 삭제된 사용자이면 `USER_NOT_FOUND` 예외를 발생시킨다.
- 자기 자신과 1대1 채팅방을 생성하는 요청은 `CANNOT_CREATE_CHAT_ROOM_WITH_SELF` 예외로 차단한다.
- 요청한 `friendId`가 존재하지 않거나 활성 친구가 아니면 `CANNOT_CREATE_CHAT_ROOM_WITH_INVALID_USER` 예외를 발생시킨다.
- 1대1 채팅방은 두 사용자 ID를 정렬하여 `directUser1`, `directUser2`로 저장한다.
- `chatRoomType`, `directUser1`, `directUser2`의 유니크 제약을 통해 동일한 두 사용자 사이에 하나의 1대1 채팅방만 존재하도록 한다.
- 기존 1대1 채팅방이 없으면 새로운 `ChatRoom`과 두 사용자의 `ChatRoomUser`를 생성한다.
- 새로 생성된 두 `ChatRoomUser`는 `MEMBER`, `ACTIVE` 상태이며 `visibleStartMessageId`와 `unreadStartMessageId`는 `0`으로 초기화한다.
- 신규 채팅방 생성 시 두 사용자 각각에게 `ROOM_ADDED` 타입의 `ChatRoomListEvent`를 생성한다.
- 기존 1대1 채팅방이 있으면 새로운 방을 생성하지 않고 기존 `ChatRoom`을 재사용한다.
- 기존 채팅방의 가장 큰 `chatMessageId`를 조회하고, 메시지가 있으면 `chatMessageId + 1`, 메시지가 없으면 `0`을 복귀 경계값으로 사용한다.
- 기존 방에서 `LEFT` 상태인 사용자는 `ACTIVE` 상태로 변경하고 `joinedAt`을 현재 시각으로 갱신한다.
- 복귀 사용자의 `visibleStartMessageId`와 `unreadStartMessageId`를 계산한 복귀 경계값으로 설정한다.
- 따라서 복귀 전에 존재했던 메시지는 노출되지 않으며 복귀 이후 생성된 메시지부터 노출되고 읽지 않은 메시지로 판단한다.
- 이미 `ACTIVE` 상태인 사용자의 상태와 메시지 경계값은 변경하지 않는다.
- 실제로 복귀한 사용자에게만 `ROOM_ADDED` 타입의 `ChatRoomListEvent`를 생성한다.
- `ChatRoomController`는 서비스가 반환한 `ChatRoomListEvent` 목록을 Redis Pub/Sub로 발행한다.
- `RedisSubscriber`는 이벤트의 `receiverUserId`를 기준으로 `/user/queue/chatRooms/list` 경로에 전달한다.

## 채팅방 목록 이벤트
- 채팅방 목록 이벤트는 클라이언트의 채팅방 목록 화면을 갱신하기 위한 WebSocket 이벤트다.
- 클라이언트는 `/user/queue/chatRooms/list` 경로를 구독해 자신에게 필요한 채팅방 목록 변경 사항을 수신한다.
- 서버는 `ChatRoomListEvent`를 Redis Pub/Sub의 `chat:room-list` 채널로 발행한다.
- `RedisSubscriber`는 `chat:room-list` 채널의 이벤트를 수신한 뒤 `receiverUserId` 기준으로 각 유저의 `/user/queue/chatRooms/list` 경로에 전달한다.
- 일반 채팅 메시지 이벤트(`ChatEvent`)도 수신 후 `MESSAGE_SENT` 목록 이벤트로 변환되어 같은 `/user/queue/chatRooms/list` 경로에 전달된다.
- 채팅방 목록 이벤트는 목록 재조회 없이 화면을 갱신하기 위한 힌트이며, 클라이언트는 `roomId` 기준으로 기존 목록과 병합한다.

## 채팅방 목록 이벤트 처리 주의사항
- 모든 이벤트 타입이 모든 필드를 채우지는 않는다.
- `ROOM_ADDED`, `ROOM_CHANGED`는 채팅방 목록 항목을 만들거나 갱신하는 데 필요한 메타데이터를 포함한다.
- `ROOM_REMOVED`는 제거 대상 식별을 위해 `roomId`, `receiverUserId` 중심으로 사용한다.
- `MESSAGE_SENT`는 최근 메시지 정보 갱신을 위한 이벤트이며, 채팅방 기본 메타데이터는 포함하지 않는다.
- 채팅방 목록 이벤트와 채팅 메시지 이벤트는 수신 순서가 항상 보장되지 않으므로 프론트엔드는 `roomId` 기준으로 병합 처리해야 한다.

## 채팅 이벤트
- `ChatEvent`는 채팅방 내부에 표시할 메시지 이벤트다.
- 클라이언트는 채팅방 화면에 입장했을 때 `/topic/chatRooms/{roomId}`를 구독해 실시간 메시지를 수신한다.
- 동일한 `ChatEvent`는 채팅방 목록 갱신을 위한 `MESSAGE_SENT` 이벤트 생성에도 사용된다.

### 채팅 이벤트 처리 주의사항
- `eventUserIds`는 채팅방 목록 갱신 대상 유저를 계산하기 위해 사용된다.
- 삭제된 유저는 `eventUserIds`에 포함하지 않는다.
- 1대1 채팅방에서 상대가 `LEFT` 상태였다가 메시지 전송으로 복귀하면, `ChatEvent`와 별도로 `ROOM_ADDED` 목록 이벤트가 생성될 수 있다.
- `ChatEvent`와 `ChatRoomListEvent`는 서로 다른 채널로 전달되므로 클라이언트 수신 순서가 항상 보장되지는 않는다.

## WebSocket/STOMP 에러 처리

- WebSocket/STOMP 에러는 발생 위치에 따라 처리 방식이 다르다.

- `CONNECT` 단계에서 발생하는 인증 실패는 `JwtChannelInterceptor`에서 발생하고, `StompErrorHandler`가 이를 STOMP `ERROR` 프레임으로 처리하며 연결이 종료된다.

- `SUBSCRIBE` 단계에서 Principal이 없는 경우도 인증되지 않은 WebSocket 요청으로 보고 예외를 던진다. 이 예외 역시 `StompErrorHandler`에서
`ERROR` 프레임으로 처리되며 연결은 종료된다.

- 반면 채팅방 구독 권한이 없는 경우는 연결 자체를 종료할 필요가 없다. 이 경우 `ChatRoomSubscriptionInterceptor`에서 `/user/queue/errors`로 에러
메시지를 전송하고 `preSend()`에서 `null`을 반환하여 해당 구독 요청만 차단한다.

- `@MessageMapping` 내부에서 발생한 예외는 `StompMessageExceptionAdvice`에서 처리한다. `ErrorException`은 해당 `ErrorCode`에 맞는 응답으로 변환
하고, 예상하지 못한 예외는 서버 내부 오류 응답으로 변환한다. 두 경우 모두 `/user/queue/errors`로 전달되며 WebSocket 연결은 유지된다.

- 정리하면, 연결 자체가 성립하면 안 되는 오류는 `StompErrorHandler`가 `ERROR` 프레임으로 처리하고, 연결 이후 특정 요청만 실패시키면 되는 오류는
`/user/queue/errors`로 처리한다. `ERROR` 프레임은 연결 종료로 이어지므로 구독 실패나 메시지 처리 실패처럼 연결을 유지해야 하는 상황에
서는 사용하지 않는다.

## 주의사항
- 이 문서는 현재까지 확정된 WebSocket 인증과 구독 권한 검증 범위만 다룬다.
- 채팅방 나가기, 재입장, 읽음 처리 정책은 아직 리팩터링 중이므로 이 문서에 확정 정책으로 적지 않는다.
- 메시지 목록 조회는 cursor 방식의 `offsetSeq`를 사용하며, 일반 offset pagination을 사용하지 않는다.
- 현재 메시지 전송 흐름에는 `ChatRoomUserStatus.ACTIVE/LEFT` 기반 재입장 처리가 남아 있으므로, 채팅방 나가기와 재입장 정책이 확정되면 이 문서도 함께 갱신해야 한다.
- `ChatRoomUserStatus` 제거는 마지막 단계에서 진행한다.
- 중간 단계에서는 `ACTIVE/LEFT` 기반 로직과 row 존재 기반 검증이 함께 존재할 수 있으므로 배포 단위와 테스트 범위를 주의해야 한다.
