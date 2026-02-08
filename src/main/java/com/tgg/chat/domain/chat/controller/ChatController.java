package com.tgg.chat.domain.chat.controller;

import java.security.Principal;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.common.redis.pubsub.RedisPublisher;
import com.tgg.chat.domain.chat.room.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.room.repository.ChatRoomUserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
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
    private final ChatRoomUserRepository chatRoomUserRepository;
	
	@MessageMapping("/chatRooms/{chatRoomId}/message")
	public void sendMessage(
            @DestinationVariable Long chatRoomId,
            ChatMessageRequest message,
            Principal principal
    ) {

        ChatEvent chatEvent = ChatEvent.of(chatRoomId, message.getSenderId(), message.getContent());

        ChatRoomUser nextOwnerChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithUser(chatRoomId, Long.parseLong(principal.getName()))
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

		redisPublisher.publishChatEvent(chatEvent);

	}
	
}
