package com.tgg.chat.domain.chat.controller;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.domain.chat.dto.request.CreateDirectChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.CreateGroupChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.InviteUserRequestDto;
import com.tgg.chat.domain.chat.dto.request.LeaveChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.response.ChatRoomListResponseDto;
import com.tgg.chat.domain.chat.dto.response.CreateDirectChatRoomResponseDto;
import com.tgg.chat.domain.chat.dto.response.CreateGroupChatRoomResponseDto;
import com.tgg.chat.domain.chat.service.ChatRoomService;
import com.tgg.chat.domain.chat.service.ChatService;
import com.tgg.chat.exception.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "ChatRoom API", description = "채팅방 API")
@RestController
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatService chatService;

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
            Authentication authentication,
            @Valid @RequestBody CreateDirectChatRoomRequestDto requestDto
    ) {

        // Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());

        // 채팅방 생성 응답 DTO 생성
        Map<String, Object> payload = chatRoomService.createDirectChatRoom(loginUserId, requestDto);

        CreateDirectChatRoomResponseDto responseDto = (CreateDirectChatRoomResponseDto)payload.get("responseDto");
        List<ChatEvent> chatEvents = (List<ChatEvent>)payload.get("chatEvents");

        chatService.sendMessage(chatEvents);

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
                    description = "chatRoomName은 필수이며 공백일 수 없습니다.",
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
                    description = "친구가 아닌 사람과 1대1 채팅방을 생성할 수 없음",
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
            Authentication authentication,
            @Valid @RequestBody CreateGroupChatRoomRequestDto requestDto
    ) {

        // Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());

        // 채팅방 생성 응답 DTO 생성
        CreateGroupChatRoomResponseDto responseDto = chatRoomService.createGroupChatRoom(loginUserId, requestDto);

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
    public ResponseEntity<List<ChatRoomListResponseDto>> findAllChatRooms(
            Authentication authentication
    ) {

        // Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());

        // 채팅방 목록 응답 DTO 생성
        List<ChatRoomListResponseDto> responseDto = chatRoomService.findAllChatRooms(loginUserId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseDto);

    }

    @PostMapping("/chatRooms/invites")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 초대",
            description =  "채팅방에 유저들을 초대합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 유저 초대 성공",
                    content = @Content(
                            mediaType = "application/json"
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
                    description = "chatRoomId는 필수입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "채팅방 초대는 1명 이상이 필요합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "자기 자신을 채팅방에 초대할 수 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "단체 채팅방의 멤버이면서 방장이어야 친구를 초대할 수 있습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "존재하지 않거나 친구가 아닌 사용자는 초대할 수 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 채팅방 입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 채팅방에 참여 중인 사용자가 포함되어 있습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
    })
    public ResponseEntity<Void> inviteUserToChatRoom(
            Authentication authentication,
            @Valid @RequestBody InviteUserRequestDto requestDto
    ) {

        // Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());

        // 채팅방 생성 응답 DTO 생성
        chatRoomService.inviteUserToChatRoom(loginUserId, requestDto);

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
            Authentication authentication,
            @Valid @RequestBody LeaveChatRoomRequestDto requestDto
    ) {

        // Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());

        // 채팅방 생성 응답 DTO 생성
        chatRoomService.leaveChatRoom(loginUserId, requestDto);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);

    }

}
