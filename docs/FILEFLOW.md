# FILEFLOW.md

## 1. 목적과 범위

- 이 문서는 `file` 도메인의 프로필 이미지 저장·조회, 채팅 파일 메시지 저장·조회, 파일 메타데이터 조합, Redis/STOMP 이벤트 전달 흐름을 실제 서버 코드 기준으로 설명한다.
- 파일의 바이너리는 로컬 파일 시스템에, 검색과 응답에 필요한 메타데이터는 MySQL의 `stored_file` 테이블에 저장한다.
- 채팅 파일은 `chat_message`와 물리적인 외래 키로 연결하지 않고 `fileKey` 문자열 규칙으로 연결한다.
- 인증 토큰의 생성·갱신·폐기는 [AUTHFLOW.md](./AUTHFLOW.md), 일반 채팅 메시지와 채팅방 상태 정책은 [CHATFLOW.md](./CHATFLOW.md)를 함께 참고한다.
- 코드와 이 문서가 다르면 현재 코드를 기준으로 판단한다.

## 2. 구성 요소와 책임

| 구성 요소 | 서버 책임 |
|---|---|
| `StoredFileController` | multipart 요청과 경로·쿼리·쿠키 값을 받고, 서비스 결과를 HTTP 응답이나 Redis 발행으로 연결한다. |
| `StoredFileService` | 사용자·채팅방 접근 검증, 파일 형식 판별, 물리 파일 생성·조회·삭제, `StoredFile` 생성, 파일 메시지와 이벤트 구성을 담당한다. |
| `StoredFileRepository` | `StoredFile` 저장 및 `fileKey`·순서·variant 기반 단건/목록 조회를 담당한다. |
| `StoredFileMapper` | 채팅 메시지 목록 응답에 필요한 원본 파일 메타데이터를 여러 `fileKey`로 한 번에 조회한다. |
| `UserRepository` | 프로필 변경 사용자 검증과 메타데이터 이벤트 수신 사용자 조회를 담당한다. |
| `ChatRoomUserRepository` | 업로더의 활성 멤버십, 메시지 파일 열람자의 가시 범위, 이벤트 수신자를 검증·조회한다. |
| `ChatMessageRepository` | `FILE` 타입 메시지를 저장하고 파일 열람 대상 메시지를 조회한다. |
| `RedisPublisher` / `RedisSubscriber` | 파일 메시지와 프로필 변경 이벤트를 Redis Pub/Sub으로 전달한 뒤 STOMP destination으로 중계한다. |
| `JwtUtils` | 메시지 파일 조회 전용 `mediaToken`을 생성하고 서명·만료·타입을 검증한다. |

## 3. 저장 모델과 식별 규칙

### 3.1 물리 파일 저장소

- 저장 루트는 `file_root_path: ${FILE_ROOT_PATH}` 설정으로 주입된다.
- 서비스는 설정값을 `Path.of(fileRootPath)`로 변환한 뒤 서버가 생성한 파일명을 `resolve`한다.
- 애플리케이션은 저장 루트 디렉터리를 생성하지 않는다. 기동·업로드 전에 디렉터리가 존재하고 프로세스가 읽기·쓰기 권한을 가져야 한다.
- `FILE_ROOT_PATH` 환경 변수가 없으면 placeholder 해석 단계에서 기동이 실패할 수 있고, 값 자체가 유효한 경로가 아니면 서비스 Bean 생성 중 `Path.of()`가 실패할 수 있다.
- 이미지·동영상 원본은 서버가 생성한 UUID와 정규화된 확장자를 사용한다.
- 일반 파일은 확장자 없는 UUID를 물리 파일명으로 사용한다.
- 사용자가 보낸 원본 파일명은 물리 경로 구성에는 사용하지 않는다. `StoredFile.originalFileName`에 저장되고 파일 `MESSAGE_SENT` 이벤트와 메시지 목록의 `ChatEventFile.originalFileName`으로 전달된다. 채팅 이미지·동영상 원본 응답에서는 `inline`, 일반 파일 응답에서는 `attachment`의 `Content-Disposition` 파일명으로도 사용한다. 프로필 이미지와 썸네일 응답에는 `Content-Disposition`을 설정하지 않는다.

### 3.2 `StoredFile` 메타데이터

각 물리 파일은 다음 정보를 `stored_file`에 저장한다.

| 필드 | 의미 |
|---|---|
| `storedFileId` | DB 자동 증가 PK |
| `fileKey` | 프로필 또는 채팅 메시지 단위의 논리 키 |
| `storedFileName` | 파일 루트 아래의 서버 생성 물리 파일명 |
| `originalFileName` | 클라이언트가 업로드한 원본 파일명 |
| `contentType` | 업로드 시 판별한 MIME 메타데이터. 이미지·동영상 원본은 정규화한 값, 썸네일은 `image/jpeg`, 일반 파일은 Tika 탐지값을 저장한다. 일반 파일 다운로드는 이 값 대신 `application/octet-stream`으로 응답한다. |
| `fileSize` | 해당 variant 물리 파일의 바이트 크기 |
| `fileOrder` | 한 메시지 안에서 파일의 1부터 시작하는 순서. 프로필은 항상 1 |
| `storedFileVariant` | `ORIGINAL` 또는 `THUMBNAIL` |
| `fileCategory` | `IMAGE`, `VIDEO`, `FILE` |
| `createdAt` | JPA Auditing이 기록하는 생성 시각 |

- `(file_key, file_order)`에는 조회용 인덱스가 있다.
- `(fileKey, fileOrder, storedFileVariant)`의 유일성은 DB unique 제약이 아니라 현재 서비스 생성 규칙으로 유지한다.
- 이미지와 동영상은 같은 `fileKey`·`fileOrder`에 `ORIGINAL`, `THUMBNAIL` 두 행이 생긴다.
- 일반 파일은 `ORIGINAL` 한 행만 생긴다.

### 3.3 논리 키 규칙

| 용도 | `fileKey` 형식 | 예시 |
|---|---|---|
| 사용자 프로필 | `user:{userId}:{UUID}` | `user:7:...` |
| 채팅 파일 메시지 | `chat-message:{chatMessageId}` | `chat-message:153` |

- 프로필을 교체할 때마다 새 UUID가 포함된 키를 생성한다. 따라서 새 URL을 사용하면 장기 캐시와 충돌하지 않는다.
- 한 파일 메시지의 모든 첨부 파일과 모든 variant는 동일한 메시지 `fileKey`를 공유한다.

## 4. API와 인증 경계

| API | HTTP 보안 체인 | 서비스 내부 권한 검증 |
|---|---|---|
| `PUT /me/profile-image` | AccessToken 필요 | 토큰 사용자 존재·미삭제 검증 |
| `GET /profile-images/{fileKey}/thumbnail` | 공개 | 메타데이터와 물리 파일 존재 여부만 검증 |
| `GET /profile-images/{fileKey}/image` | 공개 | 메타데이터와 물리 파일 존재 여부만 검증 |
| `POST /chatRooms/{chatRoomId}/files` | AccessToken 필요 | 업로더가 해당 방의 `ACTIVE` 멤버이고 미삭제 사용자인지 검증 |
| `GET /media/messages/{chatMessageId}/files/{fileOrder}` | HTTP 필터 체인에서는 공개 | `mediaToken` 쿠키, 사용자 상태, 채팅방 멤버십, 메시지 가시 범위를 서비스에서 검증 |

- `/profile-images/**`와 `/media/**`는 `SecurityWhitelist`에 포함된다.
- 메시지 파일 조회가 완전 공개라는 뜻은 아니다. `/media/**`는 Bearer AccessToken 대신 HttpOnly `mediaToken` 쿠키를 서비스에서 검사하는 구조다.
- 프로필 이미지 조회는 현재 코드상 별도 사용자·소유자·파일 용도 검증 없이 공개된다.

## 5. 프로필 이미지 변경

### 5.1 요청

- 엔드포인트: `PUT /me/profile-image`
- Content-Type: `multipart/form-data`
- Part 이름: `userProfileImage`
- 성공: `200 OK`, 응답 Body 없음

### 5.2 서버 처리 순서

1. 보호 API용 `JwtSecurityFilter`가 AccessToken을 검증하고 `AuthenticatedUser`를 Security Context에 넣는다.
2. 컨트롤러가 인증 주체의 `userId`와 `userProfileImage` Part를 서비스에 전달한다.
3. 서비스가 사용자 행을 조회한다. 없거나 `deleted = true`이면 `USER_NOT_FOUND`를 발생시킨다.
4. Part가 없거나 비어 있으면 `PROFILE_FILE_REQUIRED`를 발생시킨다.
5. 교체 후 정리할 수 있도록 현재 `User.profileImageKey`를 보관한다.
6. `user:{userId}:{UUID}` 형식의 새 키를 만들고 영속 상태의 `User.profileImageKey`를 변경한다.
7. `ImageIO`가 실제 바이트 스트림을 읽을 수 있는지 확인하고 첫 번째 `ImageReader`의 format 이름을 구한다.
8. format이 `jpeg`, `png`, `gif`, `webp` 중 하나인지 확인한다. 파일 확장자나 요청 Content-Type만 신뢰하지 않는다.
9. GIF 또는 WebP가 애니메이션이어도 썸네일용으로 첫 프레임만 읽는다.
10. 원본 확장자를 `.jpg`, `.png`, `.gif`, `.webp` 중 하나로 정규화하고 UUID 물리 파일명을 생성한다.
11. 업로드 원본을 파일 루트에 저장한다.
12. 첫 프레임으로 최대 `320 x 320`, 종횡비 유지, JPEG 품질 `0.9`인 썸네일을 생성한다.
13. 원본은 `ORIGINAL/IMAGE`, 썸네일은 `THUMBNAIL/IMAGE`인 `StoredFile` 행 두 개를 저장한다. 두 행의 `fileOrder`는 1이다.
14. 이 사용자와 같은 채팅방 이력이 있는 사용자 중 현재 `ACTIVE`이고 삭제되지 않은 다른 사용자의 ID를 중복 없이 조회한다.
15. 기존 프로필 키가 있었다면 그 키의 모든 물리 파일 삭제를 각각 시도한다. 개별 삭제 실패는 경고 로그만 남기고 새 프로필 저장을 계속한다.
16. 기존 프로필 키의 `StoredFile` 행들을 삭제한다.
17. 트랜잭션이 커밋되면 컨트롤러가 `USER_PROFILE_IMAGE_UPDATE` 이벤트를 Redis `user:metadata` 채널에 발행한다.
18. 각 서버 인스턴스의 Redis 구독자가 대상 사용자별 `/user/queue/users/metadata`로 이벤트를 전달한다.

### 5.3 프로필 이벤트 수신자와 payload

- 수신자는 변경 사용자와 같은 방에 속한 이력이 있는 사용자 중 `receiver` 멤버십이 현재 `ACTIVE`이고 사용자가 삭제되지 않은 경우다.
- 변경 사용자 자신의 멤버십이 현재 `ACTIVE`인지는 서브쿼리에서 확인하지 않는다.
- 변경 사용자 본인은 수신자에서 제외된다.
- STOMP로 나가기 전에 내부 라우팅용 `eventUserIds`는 `null`로 지운다.
- 클라이언트에 전달되는 핵심 필드는 `userMetadataEventType=USER_PROFILE_IMAGE_UPDATE`, `userId`, `userProfileImageKey`다.

## 6. 프로필 이미지 조회

### 6.1 썸네일

- 엔드포인트: `GET /profile-images/{fileKey}/thumbnail`
- `fileKey + THUMBNAIL`로 DB 메타데이터를 조회한다.
- 파일 루트의 대상이 실제 일반 파일인지 `Files.isRegularFile`로 확인한다.
- 성공 시 항상 `Content-Type: image/jpeg`로 반환한다.
- `Cache-Control`은 public, immutable, 최대 365일이다.

### 6.2 원본

- 엔드포인트: `GET /profile-images/{fileKey}/image`
- `fileKey + ORIGINAL`로 DB 메타데이터를 조회한다.
- 실제 파일이 존재하는지 확인한 후 DB에 저장된 Content-Type으로 반환한다.
- `Cache-Control`은 썸네일과 동일하게 public, immutable, 최대 365일이다.

### 6.3 조회 실패

- DB 메타데이터가 없거나 물리 파일이 없으면 `STORED_FILE_NOT_FOUND`를 반환한다.
- DB 행은 있으나 물리 파일이 유실된 경우에도 같은 오류로 외부에 노출한다.
- 응답 Body가 `FileSystemResource`이므로 Spring MVC의 Resource 응답 처리가 적용된다. 정상적인 Range 요청은 `206 Partial Content`가 될 수 있고, 잘못된 Range는 `416 Range Not Satisfiable` 처리 경로로 들어간다.

## 7. 채팅 파일 메시지 업로드

### 7.1 요청과 입력 제한

- 엔드포인트: `POST /chatRooms/{chatRoomId}/files`
- Content-Type: `multipart/form-data`
- Part 이름: `files`
- 성공: `200 OK`, 응답 Body 없음
- 파일 목록은 1개 이상 30개 이하여야 하며 모든 항목이 null이 아니고 비어 있지 않아야 한다.
- 서비스가 계산한 전체 `MultipartFile.getSize()` 합은 3 GiB 이하여야 한다.
- 전역 multipart 설정은 개별 파일 최대 `3GB`, 요청 전체 최대 `3100MB`다. 컨테이너 multipart 제한은 컨트롤러 진입 전에 먼저 적용될 수 있다.

### 7.2 접근 검증과 메시지 생성

1. AccessToken의 `userId`로 해당 `chatRoomId`의 `ChatRoomUser`, `ChatRoom`, `User`를 함께 조회한다.
2. 멤버십이 없으면 `CHAT_ROOM_ACCESS_DENIED`다.
3. 멤버십이 `LEFT`이면 `CHAT_ROOM_ACCESS_DENIED`다.
4. 사용자가 삭제 상태이면 `USER_NOT_FOUND`다.
5. 파일 목록·개수·전체 크기를 검증한다.
6. 먼저 `ChatMessage`를 저장한다. 내용은 `파일 {개수}개`, 타입은 `FILE`, 발신자는 업로더다.
7. DB가 생성한 `chatMessageId`를 파일 논리 키와 메시지 순서 기준으로 사용한다.

### 7.3 파일별 형식 판별

각 파일을 요청 순서대로 처리하고 `fileOrder`를 1부터 증가시킨다.

1. Apache Tika가 파일 바이트로 Content-Type을 탐지한다.
2. 다음 MIME이면 `IMAGE`로 분류한다.
   - `image/jpeg`
   - `image/png`
   - `image/gif`
   - `image/webp`
3. 다음 MIME이면 `VIDEO`로 분류한다.
   - `video/mp4`, `application/mp4`, `video/x-m4v`
   - `video/quicktime`, `application/quicktime`
   - `video/webm`
4. 나머지는 모두 `FILE`로 분류한다.

### 7.4 이미지 처리

1. `ImageIO`로 실제 이미지 format과 첫 프레임을 읽는다.
2. format이 JPEG, PNG, GIF, WebP인지 다시 확인한다.
3. 원본을 UUID 기반의 정규화된 확장자로 저장한다.
4. 첫 프레임으로 최대 `320 x 320`, 종횡비 유지, JPEG 품질 `0.9` 썸네일을 만든다.
5. 동일한 `fileKey`·`fileOrder`에 `ORIGINAL/IMAGE`, `THUMBNAIL/IMAGE` 메타데이터를 만든다.

### 7.5 동영상 처리

1. 탐지 MIME에 따라 원본 확장자와 Content-Type을 `.mp4/video/mp4`, `.mov/video/quicktime`, `.webm/video/webm`으로 정규화한다.
2. 원본을 UUID 파일명으로 저장한다.
3. 시스템 `PATH`의 `ffmpeg` 실행 파일을 호출한다.
4. 오디오를 제외하고 첫 프레임 하나를 MJPEG로 추출한다.
5. 썸네일은 종횡비를 유지하면서 `320 x 320` 경계 안으로 크기를 조정하고 JPEG로 저장한다. FFmpeg filter에는 작은 원본을 확대하지 말라는 별도 조건이 없다.
6. 동일한 `fileKey`·`fileOrder`에 `ORIGINAL/VIDEO`, `THUMBNAIL/VIDEO` 메타데이터를 만든다.

### 7.6 일반 파일 처리

1. 원본 확장자를 붙이지 않은 UUID 물리 파일명으로 그대로 저장한다.
2. Tika가 탐지한 Content-Type과 클라이언트 원본 파일명을 메타데이터에 기록한다.
3. `ORIGINAL/FILE` 행만 만들며 썸네일은 만들지 않는다.

- 허용 이미지 MIME에 없는 BMP·TIFF·SVG나 지원 목록 밖의 AVI·MKV 같은 형식은 업로드 자체를 거부하지 않고 일반 `FILE`로 분류한다.
- 이미지·동영상 원본은 다시 인코딩하지 않고 그대로 저장하므로 EXIF 등 원본 메타데이터도 별도로 제거하지 않는다. 새 JPEG로 생성되는 것은 썸네일이다.

### 7.7 DB 저장과 이벤트 구성

1. 모든 파일 처리가 끝나면 수집한 `StoredFile` 행을 `saveAll`한다.
2. 원본 variant만 골라 `fileOrder`, `fileCategory`, `originalFileName`, `fileSize`로 `ChatEventFile` 목록을 만든다.
3. `ChatEvent`에는 `MESSAGE_SENT`, 방·발신자 정보, `ChatEventFile` 목록, `파일 N개`, 메시지 ID, `FILE`, 생성 시각을 넣는다.
4. 컨트롤러는 필요한 `ChatRoomListEvent` 목록을 먼저 `chat:room-list`에 발행하고, 이어 `ChatEvent`를 `chat:room:{roomId}`에 발행한다.
5. Redis 구독자는 `ChatEvent`를 `/topic/chatRooms/{roomId}`로 전달한다.
6. 같은 구독자는 `ChatEvent.eventUserIds`를 내부 라우팅에 사용해 각 사용자의 `/user/queue/chatRooms/list`에 `MESSAGE_SENT` 목록 이벤트를 보낸다.

`eventUserIds`는 사용자별 채팅방 목록 `MESSAGE_SENT`의 내부 라우팅 대상일 뿐 방 topic의 수신 제한 목록이 아니다. 구독자는 이 값을 보관한 뒤 topic payload에서는 `null`로 지우고, `/topic/chatRooms/{roomId}`에는 별도로 브로드캐스트한다.

### 7.8 DIRECT 방의 상대방 복귀

- 삭제되지 않은 기존 DIRECT 참여자들을 조회한다.
- 상대방이 `LEFT`이면 파일 메시지 ID를 기준으로 `joinChatRoom`을 호출한다.
- 상대방 상태는 `ACTIVE`가 되고 `joinedAt`은 현재 시각, `visibleStartMessageId`와 `unreadStartMessageId`는 새 파일 메시지 ID가 된다.
- 복귀 상대방에게는 업로더 미리보기, 최신 파일 메시지, unread 1개를 포함한 `ROOM_ADDED`를 보낸다.
- 복귀 상대방은 이미 전체 방 정보를 받은 것이므로 같은 저장 건의 단순 `MESSAGE_SENT` 목록 이벤트 대상에서는 제외한다. 이때 `eventUserIds`에는 업로더만 들어간다.
- `ROOM_ADDED`에는 `파일 N개`, 메시지 ID·시각·읽음 상태는 있지만 `chatEventFiles`는 없다. 복귀 상대방이 기존 topic을 구독하지 않았다면 파일 `ChatEvent`도 이미 지나갈 수 있으므로, 방을 연 뒤 topic 구독과 HTTP 메시지 목록 조회로 첨부 메타데이터를 복구해야 한다.
- 상대방이 이미 `ACTIVE`이면 삭제되지 않은 DIRECT 참여자들이 `MESSAGE_SENT` 목록 이벤트 대상이 된다.
- 상대방이 삭제되었다면 조회 결과에서 제외되므로 복귀시키거나 이벤트를 보내지 않는다.

### 7.9 GROUP 방의 채팅방 목록 `MESSAGE_SENT` 수신자

- 해당 방에서 현재 `ACTIVE`이고 삭제되지 않은 사용자 ID만 `eventUserIds`에 넣는다.
- 파일 메시지 발신자도 조건을 만족하므로 사용자별 채팅방 목록 이벤트를 받는다.
- 방 topic은 이 목록과 무관하게 해당 destination의 모든 기존 구독에 전달된다.

## 8. 채팅 메시지 목록에서 파일 메타데이터 조합

- `GET /chatRooms/{chatRoomId}/messages`가 조회한 메시지 중 타입이 `FILE`인 메시지만 `chat-message:{messageId}` 키로 변환한다.
- 키 목록이 비어 있지 않으면 MyBatis `StoredFileMapper`가 `ORIGINAL` 행만 한 번에 조회한다.
- 쿼리는 `file_key`, `file_order` 순으로 정렬한다.
- 서비스는 키별로 `ChatEventFile` 목록을 묶어 각 `ChatMessageListResponseDto.chatEventFiles`에 넣는다.
- 썸네일 행은 목록 payload에 포함하지 않는다. 클라이언트는 동일한 `messageId`와 `fileOrder`에 `storedFileVariant=THUMBNAIL`을 사용해 별도로 요청한다.
- 일반 텍스트·참여·퇴장 메시지에는 대응 파일 키가 없으므로 파일 목록이 `null`일 수 있다.

## 9. 채팅 메시지 파일 조회

### 9.1 요청

- 엔드포인트: `GET /media/messages/{chatMessageId}/files/{fileOrder}`
- 필수 쿼리: `storedFileVariant=ORIGINAL|THUMBNAIL`
- 인증 전달: HttpOnly `mediaToken` 쿠키

### 9.2 서버 권한 검증 순서

1. `mediaToken` 쿠키를 JWT로 파싱해 서명과 만료를 확인한다.
2. `type` claim이 `media`인지 확인한다.
3. `sub`에서 사용자 ID를 구한다.
4. 사용자가 존재하고 삭제되지 않았는지 확인한다.
5. `chatMessageId`로 메시지와 소속 채팅방을 조회한다.
6. 사용자가 그 채팅방의 `ChatRoomUser` 행을 갖는지 확인한다.
7. 멤버십이 `ACTIVE`인지 확인한다.
8. 메시지 ID가 사용자의 `visibleStartMessageId` 이상인지 확인한다.
9. `chat-message:{chatMessageId}` + `fileOrder` + `storedFileVariant`로 `StoredFile`을 찾는다.
10. 파일 루트에 실제 일반 파일이 존재하는지 확인한다.

- 메시지 타입 자체가 `FILE`인지 별도로 검사하지 않는다. 해당 논리 키의 `StoredFile`이 없으면 최종적으로 `STORED_FILE_NOT_FOUND`가 된다.
- `mediaToken`의 `sid`가 Redis RefreshToken 세션에 남아 있는지는 확인하지 않는다. 로그아웃 시 브라우저 쿠키는 만료시키지만 이미 복사된 MediaToken은 자체 만료 시각까지 서명·타입 검증을 통과할 수 있다.

### 9.3 응답 정책

| 분류 | 응답 방식 |
|---|---|
| `IMAGE` 원본 | 저장된 이미지 Content-Type, `inline` 파일명, private 10분 캐시 |
| `IMAGE` 썸네일 | `image/jpeg`, private 10분 캐시 |
| `VIDEO` 원본 | 저장된 동영상 Content-Type, `inline` 파일명, private 10분 캐시 |
| `VIDEO` 썸네일 | `image/jpeg`, private 10분 캐시 |
| `FILE` 원본 | `application/octet-stream`, UTF-8 파일명의 `attachment` 다운로드, 기본 보안 헤더에 의한 no-store 캐시 정책 |
| `FILE` 썸네일 요청 | 썸네일 행이 없으므로 `STORED_FILE_NOT_FOUND` |

- 일반 `200 OK` 채팅 파일 응답은 컨트롤러가 DB의 `fileSize`를 `Content-Length`로 먼저 설정한다.
- 응답 Body가 `FileSystemResource`이므로 Spring MVC가 `Accept-Ranges: bytes`를 추가하며, 유효한 Range 요청은 `206 Partial Content`로 처리될 수 있다. 단일 구간 응답의 `Content-Length`는 해당 구간 길이로 덮어쓴다. 잘못된 Range는 `416 Range Not Satisfiable` 처리 경로가 있다.
- 이미지·동영상 응답은 `Cache-Control: private, max-age=600`과 `Vary: Cookie`를 설정한다.
- 일반 파일 분기는 컨트롤러에서 Cache-Control을 직접 설정하지 않는다. 다만 현재 Spring Security 기본 헤더 writer가 성공 응답에 `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache`, `Expires: 0`을 추가하므로 최종 응답은 저장 금지 정책이다.

## 10. MediaToken 수명과 쿠키 흐름

- 로그인과 토큰 재발급 성공 시 서버는 AccessToken·RefreshToken과 함께 MediaToken도 새로 만든다.
- MediaToken은 AccessToken과 같은 10분 수명이며 `sub`, `sid`, `type=media`, `iat`, `exp`를 가진 HS256 JWT다.
- `mediaToken` 쿠키는 `HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure=false`, 최대 수명 10분으로 내려간다.
- 로그아웃은 RefreshToken 쿠키와 MediaToken 쿠키를 모두 `Max-Age=0`으로 만료시킨다.
- RefreshToken만 Redis에 저장한다. MediaToken은 서버 저장소 없이 검증한다.
- 세부 인증 흐름과 배포 시 쿠키 속성 주의사항은 [AUTHFLOW.md](./AUTHFLOW.md)를 따른다.

## 11. 오류 매핑

| 상황 | 코드 | HTTP 상태 |
|---|---|---|
| 사용자 없음 또는 삭제됨 | `U003` | 404 |
| 이미지 처리용 `ImageReader` 없음 또는 reader가 보고한 format이 허용 목록 밖 | `SF001` | 400 |
| 파일 메타데이터 또는 물리 파일 없음 | `SF002` | 404 |
| 프로필 Part 없음/비어 있음 | 현재 `PROFILE_FILE_REQUIRED`도 `SF003` | 400 |
| 채팅 파일 목록 없음/빈 항목 포함 | `SF003` | 400 |
| 채팅 파일 30개 초과 | `SF004` | 400 |
| 채팅 파일 합계 3 GiB 초과 | `SF005` | 413 |
| 채팅방 미가입·LEFT | `CR010` | 403 |
| 메시지 없음 | `CM001` | 404 |
| 보호 업로드의 Authorization 헤더 없음 | `J005` | 401 |
| 보호 업로드의 Authorization 값이 `Bearer ` 형식이 아님 | `J006` | 401 |
| JWT 서명·형식·만료·MediaToken 쿠키 누락 오류 | `J001`~`J004` | 401 |
| 유효한 JWT지만 MediaToken 타입 아님 | `J009` | 401 |
| 이미지 디코딩·파일 쓰기·썸네일 생성 등 처리되지 않은 I/O 오류 | `S001` | 500 |
| 그 밖의 처리되지 않은 FFmpeg·DB·직렬화 오류 | `S001` | 500 |

- `StoredFileController`의 Swagger 설명에는 프로필 파일 누락을 `SF006`으로 적은 부분이 있지만 실제 `ErrorCode.PROFILE_FILE_REQUIRED` 값은 `SF003`이다. 실제 응답은 코드 값을 따른다.
- `storedFileVariant` 쿼리 누락·enum 오타, 숫자 path variable 변환 실패, multipart 크기 제한처럼 컨트롤러 진입 전 또는 인자 바인딩 중 발생하는 프레임워크 예외에는 전용 핸들러가 없다. 현재는 전역 일반 예외 처리의 `S001 / 500`으로 들어갈 수 있다.

## 12. 트랜잭션과 정합성 경계

### 12.1 프로필 변경

- `saveUserProfile`은 DB 트랜잭션이지만 로컬 파일 시스템은 DB 트랜잭션에 참여하지 않는다.
- 원본 또는 썸네일을 쓰는 중 `IOException`이 발생하면 새 원본과 썸네일 삭제를 각각 시도한 뒤 실패시킨다.
- 새 물리 파일 생성 이후 DB 저장·수신자 조회·커밋 단계에서 실패하는 모든 경우를 보상 삭제하는 구조는 아니다. 이 경우 새 물리 파일이 고아 파일로 남을 수 있다.
- 기존 물리 파일 삭제는 DB 커밋 전에 실행된다. 삭제 실패는 경고만 남기고 기존 DB 메타데이터는 삭제한다.
- 기존 물리 파일을 삭제한 뒤 DB 커밋이 실패하면 DB rollback으로 이전 `profileImageKey`와 메타데이터가 복원되더라도 실제 이전 파일은 이미 사라진 불일치가 생길 수 있다.
- 프로필 저장 트랜잭션이 커밋된 뒤 Redis 발행이 실패하면 새 프로필은 저장되어 있지만 HTTP 요청은 500으로 보일 수 있고 실시간 이벤트는 유실될 수 있다.

### 12.2 채팅 파일 메시지

- `saveMessageFile`은 메시지, 멤버십 복귀, 파일 메타데이터를 하나의 DB 트랜잭션으로 처리한다.
- 파일 처리 블록에서 예외가 발생하면 그 요청 중 생성 대상으로 기록한 모든 물리 경로의 삭제를 시도하고 예외를 다시 던진다. Runtime 예외가 전파되므로 DB 변경은 롤백된다.
- 이 `try/catch`는 서비스 메서드 본문 안에서 발생한 예외만 보상한다. `saveAll()`의 flush가 지연되거나 트랜잭션 interceptor가 메서드 반환 뒤 실제 커밋하는 시점에 DB 오류가 나면 catch에 들어오지 않아 새 물리 파일이 고아로 남을 수 있다.
- 파일 삭제 자체가 실패하면 경고를 남기며 물리 파일이 고아로 남을 수 있다.
- 서비스 트랜잭션이 끝난 후 컨트롤러가 Redis에 이벤트를 발행한다. 따라서 커밋 후 Redis 직렬화·발행 실패 시 DB와 파일은 유지되지만 요청은 실패로 응답될 수 있다.
- 컨트롤러는 채팅방 목록 이벤트 배열이 비어 있어도 먼저 `chat:room-list`에 `[]`를 발행한다. 이 첫 발행이 실패하면 본 `chat:room:{id}` 이벤트는 발행되지 않지만 앞서 커밋된 DB 메시지와 물리 파일은 유지된다.
- `chat:room-list`와 `chat:room:{id}`는 별도 발행이다. 둘 사이에 원자성이나 전역 순서 보장은 없다.
- Redis Pub/Sub은 영속 큐가 아니므로 구독자가 내려가 있던 동안의 이벤트를 재전송하지 않는다. HTTP 메시지·채팅방 목록 조회가 최종 재동기화 수단이다.

## 13. 파일 수명주기와 현재 미구현 범위

- 프로필 교체 시에만 이전 프로필 `StoredFile` 행과 물리 파일을 정리한다.
- 사용자 소프트 삭제는 프로필 키나 프로필 물리 파일을 삭제하지 않는다.
- 채팅 메시지 삭제 API가 없으며 채팅 파일도 자동 삭제하지 않는다.
- 채팅방 퇴장·사용자 삭제는 기존 메시지 파일을 삭제하지 않는다. 활성 멤버십·가시 범위 검증으로 열람 가능 여부만 제어한다.
- 고아 파일 탐지·주기적 정리 작업, 저장 용량 quota, 악성코드 검사, 동영상 변환 timeout은 현재 구현되어 있지 않다.
- 조회 시 물리 경로가 일반 파일인지만 확인한다. 실제 바이트 크기·체크섬·MIME을 다시 계산하지 않으므로 응답 MIME과 크기 메타데이터는 DB 값을 신뢰한다.
- Docker 이미지는 FFmpeg를 설치하지만 로컬 실행은 별도로 `ffmpeg`를 설치하고 `PATH`에 노출해야 한다. Compose는 호스트 `/home/ubuntu/tgg-chat/files`를 컨테이너 `/chatApp/files`에 bind mount하며 현재 애플리케이션 인스턴스는 하나다.
- 바이너리는 애플리케이션이 보는 로컬 파일 경로에만 있고 Redis는 이벤트만 전달한다. 다중 애플리케이션 인스턴스로 확장할 때 공유 볼륨이 없고 요청 affinity도 보장되지 않으면 업로드와 조회를 서로 다른 인스턴스가 처리해 `SF002`가 날 수 있다. 공유 저장소 또는 확실한 affinity 중 하나가 필요하다.

## 14. 프론트엔드 관리 책임

### 14.1 인증과 쿠키

- AccessToken이 필요한 업로드 API에는 `Authorization: Bearer {accessToken}`을 보낸다.
- MediaToken은 HttpOnly라 JavaScript에서 직접 읽거나 헤더로 옮기지 않는다. 메시지 파일 요청이 쿠키를 포함하도록 동일 출처·CORS·credentials 설정을 맞춘다.
- `credentials: include` 또는 `withCredentials`는 필요조건일 뿐 SameSite 제한을 우회하지 않는다. 현재 쿠키는 `SameSite=Lax`, `Secure=false`, host-only이므로 프론트와 API가 서로 다른 site이면 fetch 또는 `<img>`·`<video>` 하위 자원 요청에서 쿠키가 빠져 `J004`가 될 수 있다. 실제 배포 site와 HTTPS 구성이 이 속성과 호환되어야 한다.
- `mediaToken` 만료로 파일 요청이 401이면 일반 API 재발급 흐름을 통해 AccessToken과 MediaToken을 함께 갱신한 뒤 요청을 재시도한다.
- 로그아웃 시 메모리의 AccessToken과 캐시한 보호 미디어 상태를 함께 비운다.

### 14.2 프로필 상태

- 프로필 변경 응답에는 새 `profileImageKey`가 없고 변경 사용자 본인에게 메타데이터 이벤트도 가지 않는다. 성공 후 서버 기준 키가 필요하면 `GET /me`로 다시 동기화한다.
- 다른 사용자의 `/user/queue/users/metadata`를 구독하고 `USER_PROFILE_IMAGE_UPDATE`를 받으면 `userId`가 일치하는 친구·멤버·메시지 발신자 표시의 키를 교체한다.
- 프로필 URL은 키가 바뀔 때 새 URL이 되므로 기존 URL에 임의 cache-busting query를 붙일 필요는 없다.

### 14.3 채팅 파일 업로드와 표시

- 업로드 HTTP 200은 파일 메시지의 ID나 메타데이터를 Body로 주지 않는다. 확정 상태는 `/topic/chatRooms/{roomId}`의 `MESSAGE_SENT` 또는 메시지 목록 재조회로 반영한다.
- 클라이언트 임시 업로드 항목을 사용한다면 서버 `messageId`가 포함된 이벤트와 중복되지 않도록 치환 규칙을 둔다.
- `chatEventFiles`의 `fileOrder`는 1부터 시작하며 화면 순서와 파일 조회 URL에 그대로 사용한다.
- `IMAGE`와 `VIDEO`는 목록/그리드에 `THUMBNAIL`, 원본 보기·재생에 `ORIGINAL`을 사용한다.
- `FILE`은 항상 `ORIGINAL`을 요청하고 다운로드 응답으로 처리한다.
- HTTP 성공과 WebSocket 이벤트는 원자적이지 않다. 이벤트가 늦거나 유실될 수 있으므로 재연결·오류 복구 시 메시지 목록을 다시 조회한다.
- Redis 발행 실패나 네트워크 단절로 HTTP 오류가 보여도 DB와 물리 파일은 이미 커밋됐을 수 있다. 업로드에는 idempotency key나 client request ID가 없으므로 즉시 같은 파일을 재전송하면 새 메시지 ID와 파일이 중복 생성될 수 있다. 먼저 메시지 목록을 재조회해 저장 여부를 확인한 뒤 재시도한다.
- 현재 CORS 설정은 `Content-Disposition`을 JavaScript에 노출하는 `Access-Control-Expose-Headers`를 설정하지 않는다. 브라우저 navigation/download는 헤더를 적용하지만 cross-origin fetch로 파일명을 읽을 수 없으므로 Blob 다운로드 UI는 `chatEventFiles.originalFileName`을 사용한다.

### 14.4 캐시와 오류

- 공개 프로필 이미지는 365일 immutable 캐시이므로 동일 인물의 새 `profileImageKey`를 받았을 때만 URL을 교체한다.
- 보호 이미지·동영상은 사용자별 private 10분 캐시이고 일반 FILE은 현재 기본 no-store 정책이다. 계정 전환이나 로그아웃 때 애플리케이션 상태에서 이전 미디어 참조를 제거한다.
- `private`은 공유 캐시 저장을 막을 뿐 서버 권한 변경 시 브라우저 캐시를 즉시 폐기하지 않는다. 정상 조회되어 10분 동안 fresh한 이미지·동영상은 퇴장·권한 변경 뒤에도 같은 쿠키 variant의 로컬 캐시에서 재사용될 수 있다.
- `THUMBNAIL`이 없는 일반 파일에 썸네일 요청을 보내지 않는다.
- 삭제·퇴장·재입장으로 `visibleStartMessageId`가 바뀔 수 있으므로 과거에 알았던 파일 URL이 계속 열릴 것이라고 가정하지 않는다.

## 15. 현재 구현상 주의할 보안·운영 경계

- 프로필 조회 서비스는 `fileKey`가 `user:` 형식인지 또는 조회 행이 실제 프로필 소유인지 확인하지 않고 `fileKey + variant`만 조회한다. 따라서 프로필 공개 URL을 파일 용도에 대한 권한 경계로 간주하면 안 된다.
- 특히 단일 파일 메시지의 `chat-message:{messageId}` 키를 프로필 원본/썸네일 경로에 전달하면 현재 조회 조건상 메시지 파일 행과 일치할 수 있다. 여러 원본 행이 일치하면 단건 Repository 반환형 때문에 서버 오류가 날 수도 있다. 보호 채팅 파일과 공개 프로필 파일의 조회 범위를 Repository 수준에서 분리하지 않은 현재 코드 특성이다.
- 단일 일반 채팅 파일을 공개 프로필 원본 경로로 조회하면 보호 media API와 달리 `application/octet-stream + attachment`가 아니라 DB의 Tika MIME과 Content-Disposition 없는 응답이 된다. HTML·SVG 같은 활성 콘텐츠가 inline 해석될 수 있고 public immutable 365일 캐시도 적용되므로, 현재 문제는 권한 우회뿐 아니라 장기 공유 캐시와 활성 콘텐츠 제공 위험을 포함한다.
- MediaToken은 Redis 세션 폐기 여부를 확인하지 않으므로 서버 측 즉시 강제 폐기 토큰이 아니다. 짧은 만료 시간과 활성 사용자·멤버십·가시 범위 검사가 추가 방어선이다.
- 방 topic 권한은 `SUBSCRIBE` 시점에만 검사한다. 퇴장·삭제 뒤 기존 구독을 서버가 제거하거나 outbound마다 다시 검사하지 않으므로 연결과 구독이 남아 있으면 파일명·크기가 포함된 이후 `ChatEvent`를 계속 받을 수 있다. 실제 파일 GET은 현재 `ACTIVE` 멤버십 검사에서 차단된다.
- `findMessageFile`에는 서비스 수준 트랜잭션이나 lock이 없다. 사용자·메시지·멤버십·파일 조회는 하나의 원자적 snapshot이 아니며 검사 직후 상태가 바뀌어도 이미 진행 중인 응답을 취소하지 않는다.
- 이미지 첫 프레임을 메모리에서 디코딩하며 픽셀 수·해상도 전용 상한이 없다. 대용량 또는 비정상 이미지의 메모리 사용량을 운영 환경에서 고려해야 한다.
- 동영상 썸네일 생성은 서버의 외부 `ffmpeg` 프로세스에 의존한다. 실행 파일이 `PATH`에 없거나 입력을 처리하지 못하면 전체 파일 메시지 저장이 실패한다.

## 16. 코드 기준 위치

- HTTP 진입점: `domain/file/controller/StoredFileController`
- 핵심 유스케이스: `domain/file/service/StoredFileService`
- 파일 메타데이터: `domain/file/entity/StoredFile`
- JPA 저장·단건 조회: `domain/file/repository/StoredFileRepository`
- 메시지 목록용 배치 조회: `domain/file/repository/StoredFileMapper`, `resources/mappers/file/StoredFileMapper.xml`
- 파일 종류: `domain/file/enums/FileCategory`, `StoredFileVariant`
- 메시지 목록 조합: `domain/chat/service/ChatMessageService`
- MediaToken: `common/security/jwt/JwtUtils`, `domain/auth/controller/AuthController`
- Redis/STOMP 중계: `common/messaging/redis/RedisPublisher`, `RedisSubscriber`
