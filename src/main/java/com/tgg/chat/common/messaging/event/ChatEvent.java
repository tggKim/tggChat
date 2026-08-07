package com.tgg.chat.common.messaging.event;

import com.tgg.chat.domain.chat.enums.ChatMessageType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ChatEvent {
    private ChatEventType chatEventType;

    private Long roomId;

    // MESSAGE_SENT 전용 필드
    private Long senderId;
    private String senderName;
    private String senderProfileImageKey;
    private List<ChatEventFile> chatEventFiles;
    private String content;
    private Long messageId;
    private ChatMessageType chatMessageType;
    private LocalDateTime createdAt;
    private List<Long> eventUserIds;

    // MESSAGE_READ 전용 필드
    private Long readerUserId;
    private Long unreadStartMessageId;

    private ChatEvent(ChatEventType chatEventType, Long roomId, Long senderId, String senderName, String senderProfileImageKey, List<ChatEventFile> chatEventFiles, String content, Long messageId, ChatMessageType chatMessageType, LocalDateTime createdAt, List<Long> eventUserIds, Long readerUserId, Long unreadStartMessageId) {
        this.chatEventType = chatEventType;
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
        this.readerUserId = readerUserId;
        this.unreadStartMessageId = unreadStartMessageId;
    }

    public static ChatEvent messageSent(Long roomId, Long senderId, String senderName, String senderProfileImageKey, List<ChatEventFile> chatEventFiles, String content, Long messageId, ChatMessageType chatMessageType, LocalDateTime createdAt, List<Long> eventUserIds) {
        return new ChatEvent(
                ChatEventType.MESSAGE_SENT,
                roomId,
                senderId,
                senderName,
                senderProfileImageKey,
                chatEventFiles,
                content,
                messageId,
                chatMessageType,
                createdAt,
                eventUserIds,
                null,
                null
        );
    }

    public static ChatEvent messageRead(Long roomId, Long readerUserId, Long unreadStartMessageId) {
        return new ChatEvent(
                ChatEventType.MESSAGE_READ,
                roomId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                readerUserId,
                unreadStartMessageId
        );
    }

    public void clearEventUserIds() {
        this.eventUserIds = null;
    }
}
