package com.tgg.chat.common.redis.pubsub;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
public class ChatEvent {
    private Long roomId;
    private Long senderId;
    private String content;

    private ChatEvent(Long roomId, Long senderId, String content) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
    }

    public static ChatEvent of(Long roomId, Long senderId, String content) {
        return new ChatEvent(roomId, senderId, content);
    }

}
