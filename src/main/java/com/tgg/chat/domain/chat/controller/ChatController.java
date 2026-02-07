package com.tgg.chat.domain.chat.controller;

import java.security.Principal;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.common.redis.pubsub.RedisPublisher;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final RedisPublisher redisPublisher;
	
	@MessageMapping("/chatRooms/{chatRoomId}/message")
	public void sendMessage(
            @DestinationVariable Long chatRoomId,
            ChatMessageRequest message,
            Principal principal
    ) {

        ChatEvent chatEvent = ChatEvent.of(chatRoomId, message.getSenderId(), message.getContent());

		redisPublisher.publishChatEvent(chatEvent);

	}
	
}
