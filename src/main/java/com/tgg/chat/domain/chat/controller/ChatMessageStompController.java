package com.tgg.chat.domain.chat.controller;

import java.security.Principal;
import java.util.List;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import com.tgg.chat.common.messaging.redis.RedisPublisher;
import com.tgg.chat.domain.chat.dto.internal.SaveChatMessageResult;
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
		
        SaveChatMessageResult saveChatMessageResult = chatMessageService.saveMessage(userId, chatRoomId, message);

        List<ChatEvent> chatEvents = saveChatMessageResult.getChatEvents();
        List<ChatRoomListEvent> chatRoomListEvents = saveChatMessageResult.getChatRoomListEvents();

        redisPublisher.publishChatRoomListEvents(chatRoomListEvents);
        chatEvents.forEach(redisPublisher::publishChatEvent);
	}
	
}
