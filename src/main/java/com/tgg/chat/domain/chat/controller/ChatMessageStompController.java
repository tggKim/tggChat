package com.tgg.chat.domain.chat.controller;

import java.security.Principal;
import java.util.List;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.redis.RedisPublisher;
import com.tgg.chat.domain.chat.service.ChatMessageService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatMessageStompController {

    private final ChatMessageService chatMessageService;
    private final RedisPublisher redisPublisher;
	
	@MessageMapping("/chatRooms/{chatRoomId}/message")
	public void sendMessage(
            @DestinationVariable Long chatRoomId,
            ChatMessageRequest message,
            Principal principal
    ) {
		Long userId = Long.parseLong(principal.getName());
		
        List<ChatEvent> chatEvents = chatMessageService.saveMessage(userId, chatRoomId, message);

        chatEvents.forEach(redisPublisher::publishChatEvent);
	}
	
}
