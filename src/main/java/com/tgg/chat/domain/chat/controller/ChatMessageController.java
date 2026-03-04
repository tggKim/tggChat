package com.tgg.chat.domain.chat.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tgg.chat.domain.chat.dto.request.ChatMessageListRequestDto;
import com.tgg.chat.domain.chat.dto.response.ChatMessageListResponseDto;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.service.ChatMessageService;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "ChatMessage API", description = "채팅메시지 API")
@RestController
@RequiredArgsConstructor
public class ChatMessageController {
	private final ChatMessageService chatMessageService;
	
	@GetMapping("/chatMessages")
	public List<ChatMessageListResponseDto> findChatMessages(Authentication authentication, @Valid @RequestBody ChatMessageListRequestDto chatMessageListRequestDto) {
		// Authentication 에서 로그인한 유저의 userId 추출
        Claims claims = (Claims)authentication.getPrincipal();
        Long loginUserId = Long.parseLong(claims.getSubject());
		
		return chatMessageService.findChatMessages(loginUserId, chatMessageListRequestDto);
	}
}
