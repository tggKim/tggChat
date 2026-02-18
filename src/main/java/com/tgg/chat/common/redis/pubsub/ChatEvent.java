package com.tgg.chat.common.redis.pubsub;

import com.tgg.chat.domain.chat.enums.ChatMessageType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ChatEvent {
    private Long roomId;
    private Long senderId;
    private String content;
    private Long messageSeq;
    private ChatMessageType chatMessageType;
    private LocalDateTime createdAt;
    private Long unreadCount;
    private List<Long> eventUserIds;

    private ChatEvent(Long roomId, Long senderId, String content, Long messageSeq, ChatMessageType chatMessageType, LocalDateTime createdAt, Long unreadCount, List<Long> eventUserIds) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.messageSeq = messageSeq;
        this.chatMessageType = chatMessageType;
        this.createdAt = createdAt;
        this.unreadCount = unreadCount;
        this.eventUserIds = eventUserIds;
    }

    public static ChatEvent of(Long roomId, Long senderId, String content, Long messageSeq, ChatMessageType chatMessageType, LocalDateTime createdAt, Long unreadCount, List<Long> eventUserIds) {
        return new ChatEvent(roomId, senderId, content, messageSeq, chatMessageType, createdAt, unreadCount, eventUserIds);
    }

}
