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
- `ChatController`는 STOMP 세션의 `Principal`에서 로그인 유저 ID를 추출한다.
- `ChatMessageService.saveMessage()`는 요청 유저가 채팅방에 속한 유저인지 확인한다.
- 요청 유저가 채팅방에서 나간 상태이거나 삭제된 유저이면 메시지 저장을 차단한다.
- 메시지 순서 보장을 위해 `ChatRoom`을 비관적 락으로 조회하고 현재 `lastMessageSeq`를 기준으로 다음 메시지 seq를 계산한다.
- 1대1 채팅방의 경우 상대 유저가 삭제되지 않고 `LEFT` 상태이면 다시 `ACTIVE` 상태로 복귀시키는 흐름이 포함되어 있으며 삭제되지 않은 유저만 이벤트 수신 대상에 포함한다.
- 단체 채팅방의 경우 현재 `ACTIVE` 상태이고 삭제되지 않은 유저만 이벤트 수신 대상에 포함한다.
- 메시지를 `chat_message` 테이블에 저장한 뒤 `ChatEvent`를 생성한다.
- `ChatRoom`의 마지막 메시지 seq, 마지막 메시지 내용, 마지막 메시지 시각을 갱신한다.
- `ChatMessageService.sendMessage()`는 생성된 `ChatEvent`를 Redis Pub/Sub로 발행한다.
- `RedisPublisher`는 `chat:room:{roomId}` 채널로 `ChatEvent` JSON을 발행한다.
- `RedisSubscriber`는 `chat:room:*` 패턴으로 이벤트를 수신하고 `ChatEvent`로 역직렬화한다.
- 수신된 이벤트는 `/topic/chatRooms/{roomId}`로 브로드캐스트되어 해당 채팅방을 구독 중인 클라이언트에게 전달된다.
- 동일한 이벤트는 `eventUserIds`에 포함된 유저들의 `/user/queue/chatRooms/list` 경로로도 전달되어 채팅방 목록 갱신에 사용된다.

## 메시지 목록 조회 흐름
- 대상 엔드포인트는 `GET /chatRooms/{chatRoomId}/messages`다.
- 클라이언트는 최초 조회 시 `offsetSeq` 없이 요청한다.
- `offsetSeq`가 없으면 최신 메시지부터 최대 100개를 조회한다.
- 이전 메시지를 추가로 조회할 때는 현재 목록에서 가장 작은 메시지 seq를 `offsetSeq`로 전달한다.
- `ChatMessageService.findChatMessages()`는 요청 유저가 해당 채팅방의 `ACTIVE` 멤버이고 삭제되지 않은 유저인지 먼저 검증한다.
- 검증에 실패하면 `CHAT_ROOM_ACCESS_DENIED` 예외를 던진다.
- 메시지 조회 쿼리는 요청 유저의 `ChatRoomUser.historyStartSeq`보다 큰 seq의 메시지만 조회한다.
- 따라서 유저가 채팅방에 새로 참여하거나 재입장하기 전의 메시지는 조회되지 않는다.
- 조회 결과는 `seq` 내림차순으로 정렬되며 최대 100개를 반환한다.
- 각 메시지는 발신자 ID, 발신자명, 발신자 프로필 이미지 키를 함께 반환한다.
- 삭제된 발신자의 메시지는 유지하되 발신자 정보는 `null`로 반환한다.
- `unreadCount`는 현재 `ACTIVE` 상태이고 삭제되지 않았으며, 해당 메시지를 아직 읽지 않은 유저 수로 계산한다.
- 채팅방을 나간 유저와 삭제된 유저는 `unreadCount` 계산에서 제외한다.

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
