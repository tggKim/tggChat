package com.tgg.chat.common.messaging.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import com.tgg.chat.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);

            if(channel.equals("chat:room-list")) {
                handleChatRoomListEvents(payload);
            } else if(channel.startsWith("chat:room:")) {
                handleChatEvent(payload);
            }
        } catch (Exception e) {
            ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

            log.error("[UnhandledException] code={}, status={}, message={}",
                    errorCode.getCode(),
                    errorCode.getStatus().value(),
                    errorCode.getMessage(),
                    e);
        }
    }

    private void handleChatEvent(String payload) throws JsonProcessingException {
        // ChatEvent 로 역직렬화
        ChatEvent chatEvent = objectMapper.readValue(payload, ChatEvent.class);

        // 채팅방에서 구독중인 /topic/chatRooms/* 경로로 발행한다.
        messagingTemplate.convertAndSend("/topic/chatRooms/" + chatEvent.getRoomId(), chatEvent);

        // 채팅방 목록에서 구독중인 /user/queue/chatRooms/list 경로로 발행.
        ChatRoomListEvent chatRoomListEvent = ChatRoomListEvent.messageSent(
                chatEvent.getRoomId(),
                chatEvent.getContent(),
                chatEvent.getMessageId(),
                chatEvent.getCreatedAt()
        );
        chatEvent.getEventUserIds().forEach(userId -> {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/chatRooms/list", chatRoomListEvent);
        });
    }

    private void handleChatRoomListEvents(String payload) throws JsonProcessingException {
        // ChatRoomListEvent 리스트로 역직렬화
        List<ChatRoomListEvent> chatRoomListEvents = objectMapper.readValue(payload, new TypeReference<List<ChatRoomListEvent>>() {});

        chatRoomListEvents.forEach(chatRoomListEvent -> {
            messagingTemplate.convertAndSendToUser(String.valueOf(chatRoomListEvent.getReceiverUserId()), "/queue/chatRooms/list", chatRoomListEvent);
        });
    }
}
