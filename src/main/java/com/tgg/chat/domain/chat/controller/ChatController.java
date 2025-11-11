package com.tgg.chat.domain.chat.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatController {

	private final SimpMessagingTemplate template;
	
	@MessageMapping("/chat.send")
	public void sendMessage(ChatMessageRequest message) {
		template.convertAndSend("/topic/" + message.getRoomId(), message);
	}
	
}
