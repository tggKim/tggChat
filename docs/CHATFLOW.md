# CHATFLOW.md

## 목적
- 채팅 도메인의 현재 WebSocket 설정과 STOMP 인증, 구독 권한 검증 흐름을 정리한다.
- 채팅방 멤버십 모델은 현재 단계적으로 전환 중이므로, 아직 확정되지 않은 메시지 조회, 나가기, 재입장 정책은 이 문서에 고정하지 않는다.

## 현재 전환 상태
- 기존 채팅방 멤버십 로직에는 `ChatRoomUserStatus.ACTIVE`, `ChatRoomUserStatus.LEFT` 기반 흐름이 남아 있다.
- 최종적으로는 `ChatRoomUser`를 현재 채팅방 멤버만 표현하는 방향으로 단순화할 예정이지만, 관련 로직을 한 번에 제거하지 않는다.
- WebSocket 구독 권한 검증부터 단계적으로 정리하고, 이후 메시지 조회, 채팅방 목록, 나가기, 재입장 흐름은 별도 판단 후 수정한다.
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
- 채팅방 나가기, 재입장, 메시지 조회 범위, 읽음 처리 정책은 아직 리팩터링 중이므로 이 문서에 확정 정책으로 적지 않는다.
- `ChatRoomUserStatus` 제거는 마지막 단계에서 진행한다.
- 중간 단계에서는 `ACTIVE/LEFT` 기반 로직과 row 존재 기반 검증이 함께 존재할 수 있으므로 배포 단위와 테스트 범위를 주의해야 한다.
