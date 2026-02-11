package com.tgg.chat.domain.chat.controller;

import java.security.Principal;
import java.util.List;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.domain.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
	
	@MessageMapping("/chatRooms/{chatRoomId}/message")
	public void sendMessage(
            @DestinationVariable Long chatRoomId,
            ChatMessageRequest message,
            Principal principal
    ) {

		Long userId = Long.parseLong(principal.getName());
		
        List<ChatEvent> chatEvents = chatService.saveMessage(userId, chatRoomId, message);
        
        chatService.sendMessage(chatEvents);

	}
	
}
