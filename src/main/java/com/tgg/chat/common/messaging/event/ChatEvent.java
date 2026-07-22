package com.tgg.chat.common.messaging.event;

import com.tgg.chat.domain.chat.enums.ChatMessageType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ChatEvent {
    private Long roomId;
    private Long senderId;
    private String senderName;
    private String senderProfileImageKey;
    private List<ChatEventFile> chatEventFiles;
    private String content;
    private Long messageId;
    private ChatMessageType chatMessageType;
    private LocalDateTime createdAt;
    private List<Long> eventUserIds;

    private ChatEvent(Long roomId, Long senderId, String senderName, String senderProfileImageKey, List<ChatEventFile> chatEventFiles, String content, Long messageId, ChatMessageType chatMessageType, LocalDateTime createdAt, List<Long> eventUserIds) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderProfileImageKey = senderProfileImageKey;
        this.chatEventFiles = chatEventFiles;
        this.content = content;
        this.messageId = messageId;
        this.chatMessageType = chatMessageType;
        this.createdAt = createdAt;
        this.eventUserIds = eventUserIds;
    }

    public static ChatEvent of(Long roomId, Long senderId, String senderName, String senderProfileImageKey, List<ChatEventFile> chatEventFiles, String content, Long messageId, ChatMessageType chatMessageType, LocalDateTime createdAt, List<Long> eventUserIds) {
        return new ChatEvent(roomId, senderId, senderName, senderProfileImageKey, chatEventFiles, content, messageId, chatMessageType, createdAt, eventUserIds);
    }
}
