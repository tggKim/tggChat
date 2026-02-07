package com.tgg.chat.common.redis.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper om;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {

            // Message 에서 body를 추출한 뒤 ChatEvent로 변경
            String eventString = new String(message.getBody());
            ChatEvent event = om.readValue(eventString, ChatEvent.class);

            // 레디스 통해 받은 메시지를 STOMP 구독자들에게 전파
            messagingTemplate.convertAndSend("/topic/chatRooms/" + event.getRoomId(), event);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
