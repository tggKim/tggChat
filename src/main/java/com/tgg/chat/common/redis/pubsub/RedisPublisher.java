package com.tgg.chat.common.redis.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisPublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publishChatEvent(ChatEvent event) {

        try {

            // 메시지 발행할 채널 생성
            String channel = "chat:room:" + event.getRoomId();

            // redisTemplate 에 value를 String으로 설정했으므로 변경하는 과정 필요
            String payload = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, payload);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

}
