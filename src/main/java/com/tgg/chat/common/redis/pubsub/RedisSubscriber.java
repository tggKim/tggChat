package com.tgg.chat.common.redis.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper om;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {

            // Message 에서 body를 추출한 뒤 ChatEvent로 역직렬화
            String eventString = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatEvent event = om.readValue(eventString, ChatEvent.class);

            // STOMP 의 /topic/chatRooms/* 경로로 발행한다.
            messagingTemplate.convertAndSend("/topic/chatRooms/" + event.getRoomId(), event);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
