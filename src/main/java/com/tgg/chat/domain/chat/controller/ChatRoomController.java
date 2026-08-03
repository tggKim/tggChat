package com.tgg.chat.domain.chat.controller;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import com.tgg.chat.common.messaging.redis.RedisPublisher;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.chat.dto.internal.*;
import com.tgg.chat.domain.chat.dto.request.*;
import com.tgg.chat.domain.chat.dto.response.*;
import com.tgg.chat.domain.chat.service.ChatRoomService;
import com.tgg.chat.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ChatRoom API", description = "채팅방 API")
@RestController
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final RedisPublisher redisPublisher;

    @GetMapping("/chatRooms/{chatRoomId}/readStatuses")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방의 유저별 메시지 읽음 범위 조회",
            description =  "채팅방의 유저별 메시지 읽음 범위 조회"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방의 유저별 메시지 읽음 범위 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = ChatRoomReadStatusResponseDto.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "채팅방에 접근할 권한이 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 유저입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<List<ChatRoomReadStatusResponseDto>> findReadStatuses(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long chatRoomId
    ) {
        List<ChatRoomReadStatusResponseDto> chatRoomReadStatusResponseDtos = chatRoomService.findReadStatuses(authenticatedUser.getUserId(), chatRoomId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(chatRoomReadStatusResponseDtos);
    }

    @PostMapping("/directChatRooms")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "1대1 채팅방 생성",
            description =  "1대1 채팅방을 생성합니다"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "1대1 채팅방 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateDirectChatRoomResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "friendId 는 필수입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "자기 자신과 채팅방을 만들 수 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "존재하지 않거나 친구가 아닌 사용자는 채팅방을 생성할 수 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<CreateDirectChatRoomResponseDto> createDirectChatRoom(
    		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateDirectChatRoomRequestDto requestDto
    ) {
        CreateDirectChatRoomResult createDirectChatRoomResult = chatRoomService.createDirectChatRoom(authenticatedUser.getUserId(), requestDto);

        CreateDirectChatRoomResponseDto responseDto = createDirectChatRoomResult.getResponseDto();

        List<ChatRoomListEvent> chatRoomListEvents = createDirectChatRoomResult.getChatRoomListEvents();
        if(!chatRoomListEvents.isEmpty()) {
            redisPublisher.publishChatRoomListEvents(chatRoomListEvents);
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseDto);
    }
    
    @PostMapping("/groupChatRooms")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "단체 채팅방 생성",
            description =  "단체 채팅방을 생성합니다"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "단체 채팅방 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateGroupChatRoomResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "friendIds는 필수입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "단체 채팅은 2명 이상이 필요합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "자기 자신과 채팅방을 만들 수 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "존재하지 않거나 친구가 아닌 사용자와 채팅방을 생성할 수 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 유저",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<CreateGroupChatRoomResponseDto> createGroupChatRoom(
    		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateGroupChatRoomRequestDto requestDto
    ) {
        CreateGroupChatRoomResult createGroupChatRoomResult = chatRoomService.createGroupChatRoom(authenticatedUser.getUserId(), requestDto);

        CreateGroupChatRoomResponseDto responseDto = createGroupChatRoomResult.getResponseDto();
        List<ChatRoomListEvent> chatRoomListEvents = createGroupChatRoomResult.getChatRoomListEvents();

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseDto);
    }

    @PostMapping("/directChatRooms/{chatRoomId}/invites")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "1대1 채팅방 사용자 초대",
            description = """
                1대1 채팅방에 새로운 사용자를 초대하고 단체 채팅방으로 전환합니다.
                기존 1대1 채팅방의 LEFT 사용자는 요청 포함 여부와 관계없이 복귀시키며,
                기존 1대1 참여자가 아닌 새로운 사용자가 한 명 이상 포함되어야 합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "사용자 초대 및 단체 채팅방 전환 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                        C001: 요청 DTO에서 friendIds 가 누락된 경우
                        CR005: 존재하지 않거나 친구가 아닌 사용자를 초대한 경우
                        CR006: 초대할 사용자가 한 명도 없는 경우
                        CR007: 자기 자신을 초대한 경우
                        CR013: 기존 1대1 참여자가 아닌 새로운 사용자가 포함되지 않은 경우
                        CR015: 단체 채팅방에 1대1 채팅방 초대 API를 사용한 경우
                        CR017: 기존 1대1 채팅방 참여자가 삭제되어 단체 채팅방으로 전환할 수 없는 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                        CR010: 요청자가 채팅방에 속하지 않았거나 나간 상태인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                        U003: 요청 사용자가 삭제된 사용자인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Void> inviteUserToDirectChatRoom(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody InviteUserRequestDto requestDto
    ) {
        InviteUserToDirectChatRoomResult inviteUserToDirectChatRoomResult = chatRoomService.inviteUserToDirectChatRoom(authenticatedUser.getUserId(), chatRoomId, requestDto);

        List<ChatRoomListEvent> chatRoomListEvents = inviteUserToDirectChatRoomResult.getChatRoomListEvents();
        ChatEvent chatEvent = inviteUserToDirectChatRoomResult.getChatEvent();

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);
        redisPublisher.publishChatEvent(chatEvent);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

    @PostMapping("/groupChatRooms/{chatRoomId}/invites")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "단체 채팅방 사용자 초대",
            description = "단체 채팅방에 새로운 사용자를 초대하거나 나간 사용자를 복귀시킵니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "단체 채팅방 사용자 초대 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                        C001: 요청 DTO 에서 friendIds 가 누락된 경우
                        CR005: 존재하지 않거나 친구가 아닌 사용자를 초대한 경우
                        CR006: 초대할 사용자가 한 명도 없는 경우
                        CR007: 자기 자신을 초대한 경우
                        CR014: 1대1 채팅방에 단체 채팅방 초대 API를 사용한 경우
                        CR016: 요청한 사용자가 모두 이미 채팅방에 참여 중인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                        CR010: 요청자가 채팅방에 속하지 않았거나 나간 상태인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                        U003: 요청 사용자가 존재하지 않거나 삭제된 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Void> inviteUserToGroupChatRoom(
            @PathVariable Long chatRoomId,
    		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody InviteUserRequestDto requestDto
    ) {
        InviteUserToGroupChatRoomResult inviteUserToGroupChatRoomResult = chatRoomService.inviteUserToGroupChatRoom(authenticatedUser.getUserId(), chatRoomId, requestDto);

        List<ChatRoomListEvent> chatRoomListEvents = inviteUserToGroupChatRoomResult.getChatRoomListEvents();
        ChatEvent chatEvent = inviteUserToGroupChatRoomResult.getChatEvent();

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);
        redisPublisher.publishChatEvent(chatEvent);
        
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }
    
    @PostMapping("/chatRooms/{chatRoomId}/leave")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 나가기",
            description =  "채팅방에서 나갑니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 나가기 성공",
                    content = @Content(
                            mediaType = "application/json"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "방장 권한을 양도해야 하지만 유효한 nextOwnerId가 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "채팅방이 존재하지 않거나, 채팅방의 유저가 아닙니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "방장을 양도할 수 없는 멤버입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Void> leaveChatRoom(
            @PathVariable Long chatRoomId,
    		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody LeaveChatRoomRequestDto requestDto
    ) {
        LeaveChatRoomResult leaveChatRoomResult = chatRoomService.leaveChatRoom(authenticatedUser.getUserId(), chatRoomId,  requestDto);

        List<ChatRoomListEvent> chatRoomListEvents = leaveChatRoomResult.getChatRoomListEvents();
        ChatEvent chatEvent = leaveChatRoomResult.getChatEvent();

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);
        if(chatEvent != null) {
            redisPublisher.publishChatEvent(chatEvent);
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

    @PatchMapping("/chatRooms/{chatRoomId}/name")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 기본 이름 변경",
            description = "단체 채팅방의 방장이 공통 채팅방 이름을 변경합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 기본 이름 변경 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                        C001: roomName이 없거나 공백이거나 100자를 초과한 경우
                        CR018: 1대1 채팅방의 공통 이름 변경을 요청한 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                        CR010: 요청자가 채팅방에 속하지 않았거나 나간 상태인 경우
                        CR019: 요청자가 채팅방 방장이 아닌 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "U003: 요청 사용자가 삭제된 사용자인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Void> updateRoomName(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateRoomNameRequestDto requestDto
    ) {
        UpdateRoomNameResult updateRoomNameResult = chatRoomService.updateRoomName(authenticatedUser.getUserId(), chatRoomId,  requestDto);

        List<ChatRoomListEvent> chatRoomListEvents = updateRoomNameResult.getChatRoomListEvents();

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

    @PatchMapping("/chatRooms/{chatRoomId}/customName")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "사용자별 채팅방 이름 변경",
            description = """
                요청 사용자의 개인 채팅방 이름을 변경합니다.
                변경된 이름은 요청 사용자에게만 적용되며,
                1대1 채팅방과 단체 채팅방 모두 변경할 수 있습니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "사용자별 채팅방 이름 변경 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                        C001: customRoomName이 없거나 공백이거나 100자를 초과한 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                        CR010: 요청자가 채팅방에 속하지 않았거나 나간 상태인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                        U003: 요청 사용자가 삭제된 사용자인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Void> updateCustomRoomName(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateCustomRoomNameRequestDto requestDto
    ) {
        UpdateCustomRoomNameResult updateCustomRoomNameResult = chatRoomService.updateCustomRoomName(authenticatedUser.getUserId(), chatRoomId,  requestDto);

        List<ChatRoomListEvent> chatRoomListEvents = updateCustomRoomNameResult.getChatRoomListEvents();

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

    @GetMapping("/chatRooms/{chatRoomId}/invitableFriends")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 초대 가능 친구 목록 조회",
            description = "요청자의 친구 중 해당 채팅방 초대 정책에 따라 새로 초대하거나 복귀시킬 수 있는 사용자를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 초대 가능 친구 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = FindInvitableFriendsResponseDto.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                        CR010: 요청자가 채팅방에 속하지 않았거나 나간 상태인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                        U003: 요청 사용자가 삭제된 사용자인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<List<FindInvitableFriendsResponseDto>> findInvitableFriends(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        List<FindInvitableFriendsResponseDto> findInvitableFriendsResponseDtos = chatRoomService.findInvitableFriends(authenticatedUser.getUserId(), chatRoomId);

        return ResponseEntity.status(HttpStatus.OK).body(findInvitableFriendsResponseDtos);
    }

    @GetMapping("/chatRooms/{chatRoomId}/members")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 참여자 목록 조회",
            description = """
                채팅방에 표시할 참여자 정보를 조회합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 참여자 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = FindChatRoomMembersResponseDto.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                        CR010: 요청자가 채팅방에 속하지 않았거나 나간 상태인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                        U003: 요청 사용자가 삭제된 사용자인 경우
                        """,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<List<FindChatRoomMembersResponseDto>> findChatRoomMembers(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        List<FindChatRoomMembersResponseDto> chatRoomMembersResponseDtos = chatRoomService.findChatRoomMembers(authenticatedUser.getUserId(), chatRoomId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(chatRoomMembersResponseDtos);
    }

    @GetMapping("/chatRooms")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 목록 조회",
            description = """
                현재 사용자가 참여 중인 채팅방 목록을 조회합니다.
                채팅방 목록은 최근 활동 시각을 기준으로 내림차순 정렬됩니다.
                참여 중인 채팅방이 없으면 빈 배열을 반환합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = ChatRoomListResponseDto.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 사용자입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<List<ChatRoomListResponseDto>> findAllChatRooms(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        List<ChatRoomListResponseDto> responseDto = chatRoomService.findAllChatRooms(authenticatedUser.getUserId());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseDto);
    }
}
