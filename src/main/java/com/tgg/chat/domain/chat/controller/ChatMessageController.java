package com.tgg.chat.domain.chat.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.chat.dto.response.ChatMessageListResponseDto;
import com.tgg.chat.domain.chat.service.ChatMessageService;
import com.tgg.chat.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "ChatMessage API", description = "채팅메시지 API")
@RestController
@RequiredArgsConstructor
public class ChatMessageController {
	private final ChatMessageService chatMessageService;
	
	@GetMapping("/chatRooms/{chatRoomId}/messages")
    @SecurityRequirement(name = "JWT Auth")
    @Operation(
            summary = "채팅방 메시지 리스트 반환",
            description =  "채팅방 메시지 리스트를 반환 합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "채팅방 메시지 리스트를 반환 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChatMessageListResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "chatRoomId 은 필수입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "offsetSeq 은 필수입니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
	public List<ChatMessageListResponseDto> findChatMessages(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long chatRoomId, @RequestParam(required = false) Long offsetSeq) {
		return chatMessageService.findChatMessages(authenticatedUser.getUserId(), chatRoomId, offsetSeq);
	}
}
