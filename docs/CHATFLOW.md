# CHATFLOW.md

## 목적
- 채팅 도메인의 HTTP API, WebSocket/STOMP 통신, Redis Pub/Sub, 채팅방 참여, 메시지 조회 및 읽음 처리 흐름을 정리한다.
- 현재 구현된 `ChatRoomUserStatus.ACTIVE/LEFT`, `visibleStartMessageId`, `unreadStartMessageId` 정책을 기준으로 한다.
- 코드와 문서가 다를 경우 현재 코드를 기준으로 판단한다.

## 핵심 멤버십 정책
- `ChatRoomUserStatus.ACTIVE`는 현재 채팅방에 참여 중인 상태다.
- `ChatRoomUserStatus.LEFT`는 채팅방에서 나간 상태다.
- 채팅방에서 나가더라도 `ChatRoomUser` 행은 삭제하지 않는다.
- 재입장하면 기존 `ChatRoomUser`를 다시 `ACTIVE`로 변경한다.
- `visibleStartMessageId`는 사용자에게 표시할 수 있는 최초 메시지 경계값이다.
- `unreadStartMessageId`는 해당 ID를 포함한 이후 메시지를 읽지 않았다는 경계값이다.
- 메시지 ID가 `unreadStartMessageId`보다 작으면 읽은 메시지이고, 크거나 같으면 읽지 않은 메시지다.
- `DIRECT` 채팅방은 상대방이 나가더라도 채팅방과 상대방 정보를 유지한다.
- `GROUP` 채팅방은 현재 `ACTIVE`이며 삭제되지 않은 사용자만 현재 참여자로 취급한다.

## WebSocket 설정
- WebSocket 연결 엔드포인트는 `/ws`다.
- 클라이언트가 서버로 메시지를 보낼 때는 `/app` prefix를 사용한다.
- 채팅방 단위 브로드캐스트는 `/topic` prefix를 사용한다.
- 사용자별 이벤트는 `/user` prefix를 사용한다.
- inbound channel에는 `JwtChannelInterceptor`와 `ChatRoomSubscriptionInterceptor`가 등록되어 있다.

## STOMP 인증 흐름
- `JwtChannelInterceptor`는 STOMP `CONNECT` 요청의 AccessToken을 검증한다.
- 인증에 성공하면 로그인 사용자 ID를 기반으로 `StompPrincipal`을 생성한다.
- Principal은 STOMP 세션에 저장된다.
- `@MessageMapping`에서는 Principal을 통해 현재 사용자 ID를 추출한다.
- `/user/**` 경로의 사용자별 라우팅에도 동일한 Principal 이름을 사용한다.

## 채팅방 구독 권한 검증
- `ChatRoomSubscriptionInterceptor`는 `/topic/chatRooms/{chatRoomId}`에 대한 `SUBSCRIBE` 요청을 검증한다.
- Principal이 없으면 인증되지 않은 요청으로 처리한다.
- 요청자가 해당 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- 권한이 없으면 해당 구독 요청만 차단하고 `/user/queue/errors`로 오류를 전달한다.
- 채팅방 토픽 이외의 구독 경로는 해당 인터셉터의 채팅방 권한 검증 대상이 아니다.

## 메시지 전송 흐름
- 클라이언트는 `/app/chatRooms/{chatRoomId}/message`로 메시지를 전송한다.
- `ChatMessageStompController`는 Principal에서 사용자 ID를 추출한다.
- `ChatMessageService.saveMessage()`는 요청자의 `ChatRoomUser`, `ChatRoom`, `User`를 조회한다.
- 요청자가 채팅방에 속하지 않았거나 `LEFT` 상태이면 `CHAT_ROOM_ACCESS_DENIED` 예외를 발생시킨다.
- 요청자가 삭제된 사용자이면 `USER_NOT_FOUND` 예외를 발생시킨다.
- 메시지를 `chat_message` 테이블에 저장하고 생성된 `chatMessageId`를 메시지 식별자와 정렬 기준으로 사용한다.

### DIRECT 메시지 전송
- 상대방이 삭제되지 않았고 `LEFT` 상태이면 저장된 메시지를 기준으로 상대방을 복귀시킨다.
- 복귀 사용자의 `joinedAt`을 현재 시각으로 변경한다.
- 복귀 사용자의 `visibleStartMessageId`와 `unreadStartMessageId`를 저장된 메시지 ID로 설정한다.
- 복귀 사용자는 자신을 복귀시킨 메시지부터 조회할 수 있고 해당 메시지부터 읽지 않은 상태가 된다.
- 복귀 사용자에게는 최근 메시지와 읽음 상태를 포함한 `ROOM_ADDED` 목록 이벤트를 전달한다.
- 복귀가 발생한 경우 `MESSAGE_SENT` 목록 이벤트는 기존 참여 중인 사용자에게만 전달한다.
- 상대방이 이미 `ACTIVE` 상태이면 두 사용자 모두 `MESSAGE_SENT` 목록 이벤트 대상이 된다.
- 상대방이 삭제된 사용자이면 복귀시키지 않고 현재 유효한 사용자에게만 목록 이벤트를 전달한다.

### GROUP 메시지 전송
- 현재 `ACTIVE` 상태이며 삭제되지 않은 사용자만 `eventUserIds`에 포함한다.
- 해당 사용자들은 `/user/queue/chatRooms/list`에서 `MESSAGE_SENT` 목록 이벤트를 받는다.

### 메시지 이벤트 발행
- 컨트롤러는 별도로 생성된 `ChatRoomListEvent`를 먼저 발행하고 이후 `ChatEvent`를 발행한다.
- `ChatEvent`는 Redis의 `chat:room:{roomId}` 채널로 발행한다.
- `ChatRoomListEvent` 목록은 Redis의 `chat:room-list` 채널로 발행한다.
- `RedisSubscriber`는 `ChatEvent`를 `/topic/chatRooms/{roomId}`로 브로드캐스트한다.
- `MESSAGE_SENT`의 `eventUserIds`에 포함된 사용자에게는 `/user/queue/chatRooms/list`로 목록 이벤트를 전달한다.

## 메시지 목록 조회 흐름
- 대상 엔드포인트는 `GET /chatRooms/{chatRoomId}/messages`다.
- 요청자가 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- `visibleStartMessageId`보다 크거나 같은 메시지만 조회한다.
- 최초 조회에서는 `offsetMessageId`를 전달하지 않는다.
- 최초 조회는 최신 메시지부터 최대 100개를 조회한다.
- 이전 메시지 조회 시 현재 목록에서 가장 작은 `messageId`를 `offsetMessageId`로 전달한다.
- 추가 조회에서는 `messageId < offsetMessageId` 조건을 사용한다.
- 조회 결과는 `messageId` 내림차순으로 정렬한다.
- 메시지와 발신자는 `fetch join`으로 조회한다.
- 삭제된 발신자의 메시지는 유지한다.
- 발신자가 삭제된 경우 `senderId`, `senderName`, `senderProfileImageKey`는 `null`로 응답한다.

## 채팅방 사용자별 읽음 범위 조회 흐름
- 대상 엔드포인트는 `GET /chatRooms/{chatRoomId}/readStatuses`다.
- 요청자가 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- 해당 채팅방의 `ACTIVE` 상태이며 삭제되지 않은 사용자들을 조회한다.
- 각 사용자의 `userId`, `unreadStartMessageId`를 응답한다.
- 클라이언트는 사용자별 `unreadStartMessageId`를 기준으로 메시지별 읽음 상태를 계산한다.

## 메시지 읽음 처리 흐름
- 클라이언트는 `/app/chatRooms/{chatRoomId}/read`로 `readMessageId`를 전송한다.
- 요청자가 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- `readMessageId`가 해당 채팅방에 존재하는 메시지인지 확인한다.
- 존재하지 않으면 `CHAT_MESSAGE_NOT_FOUND` 예외를 발생시킨다.
- `readMessageId`가 요청자의 `visibleStartMessageId`보다 작으면 `CHAT_MESSAGE_ACCESS_DENIED` 예외를 발생시킨다.
- 새로운 읽지 않음 경계값을 `readMessageId + 1`로 계산한다.
- 기존 `unreadStartMessageId`보다 새로운 값이 클 때만 조건부 UPDATE한다.
- 조건부 UPDATE를 통해 동시에 읽음 요청이 발생해도 커서가 이전 값으로 되돌아가지 않도록 한다.
- UPDATE 후 DB에 저장된 실제 `unreadStartMessageId`를 다시 조회한다.
- 실제 경계값 이상인 메시지 개수를 계산하여 `unreadCount`를 구한다.
- `/topic/chatRooms/{chatRoomId}`에는 `MESSAGE_READ` 타입의 `ChatEvent`를 전달한다.
- 채팅 이벤트에는 `readerUserId`, `unreadStartMessageId`를 포함한다.
- 요청자의 `/user/queue/chatRooms/list`에는 `MESSAGE_READ` 타입의 목록 이벤트를 전달한다.
- 목록 이벤트에는 `roomId`, `unreadStartMessageId`, `unreadCount`를 포함한다.

## DIRECT 채팅방 생성 흐름
- 대상 엔드포인트는 `POST /directChatRooms`다.
- 요청자가 삭제된 사용자이면 `USER_NOT_FOUND` 예외를 발생시킨다.
- 자기 자신과의 생성을 요청하면 `CANNOT_CREATE_CHAT_ROOM_WITH_SELF` 예외를 발생시킨다.
- 상대방이 요청자의 친구가 아니거나 삭제된 사용자이면 생성을 차단한다.
- 두 사용자 ID를 정렬하여 `directUser1`, `directUser2`로 저장한다.
- 동일한 두 사용자 사이에는 하나의 `DIRECT` 채팅방만 존재한다.

### 기존 채팅방이 없는 경우
- 새로운 `DIRECT` 채팅방을 생성한다.
- 두 사용자의 `ChatRoomUser`를 `MEMBER`, `ACTIVE` 상태로 생성한다.
- `visibleStartMessageId`, `unreadStartMessageId`는 `0`으로 초기화한다.
- 두 사용자에게 각각 상대방 정보를 포함한 `ROOM_ADDED` 이벤트를 전달한다.
- 메시지가 없으므로 최근 메시지 정보는 `null`이고 `lastActivityAt`은 각 사용자의 `joinedAt`이다.

### 기존 채팅방이 있는 경우
- 새로운 채팅방을 생성하지 않고 기존 채팅방을 사용한다.
- 가장 최근 메시지 ID가 있으면 `최근 메시지 ID + 1`, 없으면 `0`을 복귀 경계값으로 사용한다.
- 기존 사용자 중 `LEFT` 상태인 사용자를 `ACTIVE`로 복귀시킨다.
- 복귀 사용자의 `joinedAt`, `visibleStartMessageId`, `unreadStartMessageId`를 갱신한다.
- 실제로 복귀한 사용자에게만 `ROOM_ADDED` 이벤트를 전달한다.
- 이미 `ACTIVE` 상태인 사용자의 상태와 메시지 경계값은 변경하지 않는다.

## GROUP 채팅방 생성 흐름
- 대상 엔드포인트는 `POST /groupChatRooms`다.
- `friendIds`에서 중복 ID를 제거한다.
- 초대할 사용자가 없거나 자기 자신이 포함되면 요청을 차단한다.
- 모든 대상은 요청자의 친구이며 삭제되지 않은 사용자여야 한다.
- 요청자는 `OWNER`, 나머지 사용자는 `MEMBER` 권한을 가진다.
- 모든 참여자는 `ACTIVE` 상태로 생성된다.
- `visibleStartMessageId`, `unreadStartMessageId`는 `0`으로 초기화한다.
- `chatRoomName`이 없거나 공백이면 `roomName`을 `null`로 저장한다.
- 별도의 참여 안내 메시지는 저장하지 않는다.
- 각 사용자에게 자신을 제외한 이름순 최대 4명의 `previewUsers`를 포함한 `ROOM_ADDED` 이벤트를 전달한다.
- `memberCount`는 전체 참여자 수다.
- 최근 메시지는 `null`이고 `lastActivityAt`은 각 사용자의 `joinedAt`이다.

## DIRECT 채팅방 초대 흐름
- 대상 엔드포인트는 `POST /directChatRooms/{chatRoomId}/invites`다.
- `friendIds`에서 중복 ID를 제거한다.
- 대상이 없거나 자기 자신이 포함되면 요청을 차단한다.
- 요청자가 해당 방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- 대상 채팅방이 `DIRECT`가 아니면 그룹 채팅방 초대 API 사용 예외를 발생시킨다.
- 기존 DIRECT 참여자 중 삭제된 사용자가 있으면 그룹 채팅방으로 전환하지 않는다.
- 기존 DIRECT 참여자 외의 신규 사용자가 최소 한 명 포함되어야 한다.
- 신규 사용자는 요청자의 친구이며 삭제되지 않은 사용자여야 한다.
- 기존 DIRECT 참여자가 `LEFT` 상태이면 요청 포함 여부와 관계없이 복귀시킨다.
- 채팅방을 `GROUP`으로 변경하고 `directUser1`, `directUser2`를 초기화한다.
- 요청자의 권한을 `OWNER`로 변경한다.
- 신규 초대 사용자 이름을 정렬하여 하나의 `JOIN_TEXT` 메시지를 저장한다.
- 복귀 사용자와 신규 사용자의 경계값을 참여 안내 메시지 ID로 설정한다.
- 복귀 및 신규 사용자에게는 `ROOM_ADDED` 이벤트를 전달한다.
- 기존 활성 사용자에게는 `ROOM_CHANGED` 이벤트를 전달한다.
- 목록 이벤트 자체에 참여 안내 메시지가 포함되므로 `ChatEvent.eventUserIds`는 빈 목록으로 설정한다.
- `ChatEvent`는 채팅방 토픽에만 참여 안내 메시지를 전달하고 별도의 `MESSAGE_SENT` 목록 이벤트는 만들지 않는다.

## GROUP 채팅방 초대 흐름
- 대상 엔드포인트는 `POST /groupChatRooms/{chatRoomId}/invites`다.
- `friendIds`에서 중복 ID를 제거한다.
- 대상이 없거나 자기 자신이 포함되면 요청을 차단한다.
- 요청자가 해당 방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- 대상 채팅방이 `GROUP`이 아니면 DIRECT 채팅방 초대 API 사용 예외를 발생시킨다.
- 모든 초대 대상은 현재 요청자의 친구이며 삭제되지 않은 사용자여야 한다.
- 기존 `LEFT` 참여자는 복귀 대상으로 분류한다.
- `ChatRoomUser`가 없는 사용자는 신규 초대 대상으로 분류한다.
- 복귀 또는 신규 초대 대상이 없으면 `CHAT_ROOM_INVITEES_ALREADY_ACTIVE` 예외를 발생시킨다.
- 복귀 및 신규 사용자 이름을 정렬하여 하나의 `JOIN_TEXT` 메시지를 저장한다.
- 복귀 및 신규 사용자의 경계값을 참여 안내 메시지 ID로 설정한다.
- 복귀 및 신규 사용자에게는 `ROOM_ADDED` 이벤트를 전달한다.
- 기존 활성 사용자에게는 `ROOM_CHANGED` 이벤트를 전달한다.
- 목록 이벤트에 참여 안내 메시지가 포함되므로 `ChatEvent.eventUserIds`는 빈 목록으로 설정한다.
- `ChatEvent`는 채팅방 토픽에만 전달된다.

## 채팅방 나가기 흐름
- 대상 엔드포인트는 `POST /chatRooms/{chatRoomId}/leave`다.
- 요청자가 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- 요청자의 상태를 `LEFT`로 변경한다.
- 요청자의 `customRoomName`을 `null`로 초기화한다.

### DIRECT 채팅방 나가기
- 퇴장 안내 메시지를 저장하지 않는다.
- 상대방에게 `ROOM_CHANGED` 이벤트를 전달하지 않는다.
- 요청자에게만 `ROOM_REMOVED` 이벤트를 전달한다.
- 상대방의 채팅방과 멤버 수는 DIRECT 정책에 따라 기존 상태로 유지한다.

### GROUP 채팅방 나가기
- 요청자를 제외한 `ACTIVE`이며 삭제되지 않은 참여자를 조회한다.
- 요청자가 `OWNER`이고 다른 활성 사용자가 남아 있으면 방장 권한을 양도해야 한다.
- `nextOwnerId`가 유효한 활성 참여자가 아니면 `CHAT_ROOM_NEXT_OWNER_INVALID` 예외를 발생시킨다.
- 다른 활성 사용자가 없다면 방장 양도 없이 나간다.
- 하나의 `LEAVE_TEXT` 메시지를 저장한다.
- 남은 활성 사용자에게 인원수, 미리보기 사용자, 퇴장 메시지가 포함된 `ROOM_CHANGED` 이벤트를 전달한다.
- 퇴장 메시지의 `ChatEvent.eventUserIds`는 빈 목록으로 설정한다.
- 퇴장 메시지는 채팅방 토픽에만 전달되고 별도의 `MESSAGE_SENT` 목록 이벤트는 생성하지 않는다.
- 요청자에게는 `ROOM_REMOVED` 이벤트를 전달한다.

## 채팅방 기본 이름 변경 흐름
- 대상 엔드포인트는 `PATCH /chatRooms/{chatRoomId}/name`이다.
- 요청자가 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- `GROUP` 채팅방에서만 기본 이름을 변경할 수 있다.
- 요청자가 `OWNER`가 아니면 권한 예외를 발생시킨다.
- 이름의 앞뒤 공백을 제거하고 최대 100자로 저장한다.
- 모든 활성 사용자에게 `ROOM_NAME_CHANGED` 이벤트를 전달한다.
- 각 이벤트에는 변경된 `baseRoomName`과 해당 사용자의 `customRoomName`을 포함한다.

## 사용자별 채팅방 이름 변경 흐름
- 대상 엔드포인트는 `PATCH /chatRooms/{chatRoomId}/customName`이다.
- 요청자가 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- `DIRECT`, `GROUP` 채팅방 모두 개인 이름을 설정할 수 있다.
- 이름의 앞뒤 공백을 제거하고 최대 100자로 저장한다.
- 변경된 이름은 요청자의 `ChatRoomUser.customRoomName`에만 저장한다.
- 요청자에게만 `ROOM_NAME_CHANGED` 이벤트를 전달한다.
- 현재 API는 개인 이름의 명시적인 초기화를 지원하지 않는다.
- 사용자가 채팅방을 나가면 개인 이름은 자동으로 `null`로 초기화된다.

## 채팅방 초대 가능 친구 조회 흐름
- 대상 엔드포인트는 `GET /chatRooms/{chatRoomId}/invitableFriends`다.
- 요청자가 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- 요청자의 친구 중 삭제되지 않은 사용자만 조회한다.
- `DIRECT` 채팅방은 기존 참여 이력이 있는 상대방을 `ACTIVE/LEFT`와 관계없이 제외한다.
- `GROUP` 채팅방은 현재 `ACTIVE`인 참여자를 제외한다.
- `GROUP` 채팅방에서 `LEFT` 상태인 친구는 복귀 대상으로 목록에 포함한다.
- 결과는 사용자 이름 오름차순으로 정렬한다.
- `userId`, `username`, `profileImageKey`를 응답한다.

## 채팅방 참여자 상세 조회 흐름
- 대상 엔드포인트는 `GET /chatRooms/{chatRoomId}/members`다.
- 요청자가 채팅방의 `ACTIVE` 사용자이며 삭제되지 않았는지 검증한다.
- 삭제된 사용자는 참여자 목록에서 제외한다.
- `DIRECT` 채팅방은 `ACTIVE/LEFT` 상태와 관계없이 기존 참여자를 반환한다.
- `GROUP` 채팅방은 현재 `ACTIVE` 상태인 참여자만 반환한다.
- 결과는 사용자 이름 오름차순으로 정렬한다.
- `userId`, `username`, `profileImageKey`, `chatRoomUserRole`, `canAddFriend`를 응답한다.
- 자기 자신이거나 이미 친구인 사용자의 `canAddFriend`는 `false`다.
- 친구가 아니면서 자기 자신이 아닌 사용자의 `canAddFriend`는 `true`다.

## 채팅방 목록 조회 흐름
- 대상 엔드포인트는 `GET /chatRooms`다.
- 요청자가 존재하며 삭제되지 않았는지 검증한다.
- 요청자의 `ChatRoomUser`가 `ACTIVE` 상태인 채팅방만 조회한다.
- 참여 중인 채팅방이 없으면 빈 배열을 반환한다.
- 목록 조회는 총 다섯 종류의 조회 결과를 서비스에서 조합한다.

### 채팅방 기본 정보
- `roomId`, `roomType`, `baseRoomName`, `customRoomName`, `myRole`, `joinedAt`, `unreadStartMessageId`를 조회한다.

### 채팅방 인원수
- 삭제된 사용자는 제외한다.
- `DIRECT` 채팅방은 `ACTIVE/LEFT` 상태와 관계없이 인원수에 포함한다.
- `GROUP` 채팅방은 `ACTIVE` 사용자만 포함한다.

### 미리보기 사용자
- 요청자와 삭제된 사용자를 제외한다.
- 이름순으로 최대 4명까지 제공한다.
- `DIRECT` 채팅방은 `ACTIVE/LEFT` 상태와 관계없이 포함한다.
- `GROUP` 채팅방은 `ACTIVE` 사용자만 포함한다.

### 최근 메시지
- 요청자의 `visibleStartMessageId`보다 크거나 같은 메시지만 대상으로 한다.
- 채팅방별 가장 큰 `chatMessageId`를 가진 메시지를 최근 메시지로 사용한다.
- 메시지가 없으면 `lastMessagePreview`, `messageId`는 `null`이다.

### 안 읽은 메시지 개수
- 요청자의 `unreadStartMessageId`보다 크거나 같은 메시지 개수를 계산한다.
- 해당 메시지가 없으면 `unreadCount`는 `0`이다.

### 최종 응답
- 최근 메시지가 있으면 메시지 생성 시각을 `lastActivityAt`으로 사용한다.
- 최근 메시지가 없으면 요청자의 `joinedAt`을 `lastActivityAt`으로 사용한다.
- 최종 목록은 `lastActivityAt` 내림차순으로 정렬한다.
- 채팅방 표시 이름은 프론트에서 `customRoomName`, `baseRoomName`, `previewUsers` 이름 조합 순서로 결정한다.

## 채팅방 목록 이벤트
- 클라이언트는 `/user/queue/chatRooms/list`를 구독한다.
- 서버에서 직접 생성한 목록 이벤트는 Redis의 `chat:room-list` 채널로 발행한다.
- `RedisSubscriber`는 `receiverUserId`를 기준으로 사용자별 목록 이벤트를 전달한다.
- 일반 메시지의 `ChatEvent`는 `eventUserIds`를 기준으로 `MESSAGE_SENT` 목록 이벤트로 변환된다.

| 이벤트 타입 | 주요 필드 및 처리 |
|---|---|
| `ROOM_ADDED` | 채팅방 전체 정보, 인원수, 미리보기 사용자, 최근 메시지, 읽음 상태를 사용해 목록에 추가한다. |
| `ROOM_CHANGED` | 채팅방 정보, 인원수, 미리보기 사용자, 최근 메시지를 갱신한다. |
| `ROOM_NAME_CHANGED` | `baseRoomName`, `customRoomName`만 갱신한다. |
| `ROOM_REMOVED` | `roomId`에 해당하는 채팅방을 목록에서 제거한다. |
| `MESSAGE_SENT` | 최근 메시지, `messageId`, `lastActivityAt`을 갱신한다. |
| `MESSAGE_READ` | `unreadStartMessageId`, `unreadCount`를 갱신한다. |

## 채팅 이벤트
- 클라이언트는 채팅방 화면에 들어갈 때 `/topic/chatRooms/{roomId}`를 구독한다.
- `ChatEventType.MESSAGE_SENT`는 일반 메시지와 참여·퇴장 안내 메시지를 전달한다.
- `ChatEventType.MESSAGE_READ`는 사용자별 읽음 커서 변경을 전달한다.
- `MESSAGE_SENT`에는 발신자, 메시지, 생성 시각, `eventUserIds`가 포함된다.
- `MESSAGE_READ`에는 `readerUserId`, `unreadStartMessageId`가 포함된다.
- `MESSAGE_READ`의 `eventUserIds`는 빈 목록이므로 `MESSAGE_SENT` 목록 이벤트로 추가 전달되지 않는다.

## 프론트 이벤트 처리 주의사항
- 모든 이벤트가 `ChatRoomListEvent`의 모든 필드를 채우지는 않는다.
- 이벤트에서 `null`인 필드는 기존 상태를 덮어쓰지 않는다.
- 목록 상태는 `roomId`를 기준으로 추가, 변경, 제거한다.
- 최근 메시지는 수신한 `messageId`가 현재 저장된 값보다 큰 경우 갱신한다.
- 읽기 커서는 기존 `unreadStartMessageId`보다 작은 값으로 되돌리지 않는다.
- `ChatEvent`와 `ChatRoomListEvent`는 서로 다른 Redis 채널을 사용하므로 수신 순서가 항상 보장되지는 않는다.
- 최초 연결과 재연결 시 HTTP 채팅방 목록 조회 결과를 기준 상태로 사용한다.
- 초기 HTTP 조회 중 수신한 WebSocket 이벤트는 임시 보관한 뒤 HTTP 상태 구성 이후 동일한 이벤트 처리 함수로 적용한다.
- Redis Pub/Sub은 이벤트 재전송을 보장하지 않으므로 재연결 후 HTTP 조회로 상태를 다시 동기화한다.

## WebSocket/STOMP 오류 처리
- `CONNECT` 인증 실패는 `JwtChannelInterceptor`에서 발생한다.
- 연결이 성립하면 안 되는 오류는 `StompErrorHandler`가 STOMP `ERROR` 프레임으로 처리하며 연결이 종료된다.
- 채팅방 구독 권한이 없는 경우에는 연결을 종료하지 않고 해당 구독만 차단한다.
- 구독 권한 오류는 `/user/queue/errors`로 전달한다.
- `@MessageMapping` 내부의 `ErrorException`은 `StompMessageExceptionAdvice`에서 처리한다.
- 메시지 전송 및 읽음 처리 오류도 `/user/queue/errors`로 전달하며 WebSocket 연결은 유지한다.

## 현재 정합성 정책
- 메시지 순서와 목록의 최근 메시지 판단은 데이터베이스가 생성한 `chatMessageId`를 기준으로 한다.
- `chatMessageId`는 전체 메시지 테이블의 PK이므로 채팅방 내부에서 값이 연속적일 필요는 없다.
- 읽음 커서는 조건부 UPDATE를 사용하여 이전 값으로 되돌아가지 않도록 한다.
- 메시지 이벤트와 읽음 이벤트가 동시에 발생하면 `unreadCount`가 순간적으로 어긋날 수 있다.
- 해당 차이는 다음 읽음 이벤트 또는 HTTP 채팅방 목록 재조회로 보정하는 느슨한 정합성을 허용한다.