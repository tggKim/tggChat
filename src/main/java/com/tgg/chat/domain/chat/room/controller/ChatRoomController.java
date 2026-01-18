package com.tgg.chat.domain.chat.room.controller;

import com.tgg.chat.domain.chat.room.dto.request.CreateDirectChatRoomRequestDto;
import com.tgg.chat.domain.chat.room.dto.response.CreateDirectChatRoomResponseDto;
import com.tgg.chat.domain.chat.room.service.ChatRoomService;
import com.tgg.chat.domain.user.dto.response.UserResponseDto;
import com.tgg.chat.exception.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ChatRoom API", description = "채팅방 API")
@RestController
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping("directChatRooms")
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
                    description = "자기 자신과 1대1 채팅방 생성할 수 없음",
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 생성된 1대1 채팅방",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<CreateDirectChatRoomResponseDto> createDirectChatRoom(
            Authentication authentication,
            @RequestBody CreateDirectChatRoomRequestDto requestDto
    ) {

        // Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());

        // 채팅방 생성 응답 DTO 생성
        CreateDirectChatRoomResponseDto responseDto = chatRoomService.createDirectChatRoom(loginUserId, requestDto);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseDto);

    }

}
