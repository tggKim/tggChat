package com.tgg.chat.domain.chat.controller;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import com.tgg.chat.common.messaging.redis.RedisPublisher;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.chat.dto.internal.CreateDirectChatRoomResult;
import com.tgg.chat.domain.chat.dto.internal.CreateGroupChatRoomResult;
import com.tgg.chat.domain.chat.dto.internal.InviteUserToGroupChatRoomResult;
import com.tgg.chat.domain.chat.dto.request.CreateDirectChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.CreateGroupChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.InviteUserRequestDto;
import com.tgg.chat.domain.chat.dto.request.LeaveChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.response.ChatRoomListResponseDto;
import com.tgg.chat.domain.chat.dto.response.ChatRoomReadStatusResponseDto;
import com.tgg.chat.domain.chat.dto.response.CreateDirectChatRoomResponseDto;
import com.tgg.chat.domain.chat.dto.response.CreateGroupChatRoomResponseDto;
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

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);

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

    @GetMapping("/chatRooms")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 목록 조회",
            description =  "채팅방 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChatRoomListResponseDto.class)
                    )
            )
    })
    public ResponseEntity<ChatRoomListResponseDto> findAllChatRooms(
    		@AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        ChatRoomListResponseDto responseDto = chatRoomService.findAllChatRooms(authenticatedUser.getUserId());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseDto);
    }

    @PostMapping("/groupChatRooms/invites")
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
                        C001: 요청 DTO 에서 friendIds 또는 chatRoomId가 누락된 경우
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
    		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody InviteUserRequestDto requestDto
    ) {
        InviteUserToGroupChatRoomResult inviteUserToGroupChatRoomResult = chatRoomService.inviteUserToGroupChatRoom(authenticatedUser.getUserId(), requestDto);

        List<ChatRoomListEvent> chatRoomListEvents = inviteUserToGroupChatRoomResult.getChatRoomListEvents();
        ChatEvent chatEvent = inviteUserToGroupChatRoomResult.getChatEvent();

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);
        redisPublisher.publishChatEvent(chatEvent);
        
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }
    
    @PostMapping("/chatRooms/leave")
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
                    description = "chatRoomId는 필수입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "nextOwnerId는 필수입니다.",
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
    		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody LeaveChatRoomRequestDto requestDto
    ) {
        List<ChatEvent> chatEvents = chatRoomService.leaveChatRoom(authenticatedUser.getUserId(), requestDto);

        chatEvents.forEach(redisPublisher::publishChatEvent);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

}
