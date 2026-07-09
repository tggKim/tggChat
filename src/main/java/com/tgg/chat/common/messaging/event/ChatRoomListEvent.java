package com.tgg.chat.common.messaging.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ChatRoomListEvent {
    private ChatRoomListEventType eventType;
    private Long roomId;
    private Long receiverUserId;
    private List<Long> receiverUserIds;
    private String roomName;
    private Long memberCount;
    private String lastMessagePreview;
    private Long lastMessageSeq;
    private LocalDateTime lastMessageAt;
    private List<String> profileImageKeys;

    private ChatRoomListEvent(
            ChatRoomListEventType eventType,
            Long roomId,
            Long receiverUserId,
            List<Long> receiverUserIds,
            String roomName,
            Long memberCount,
            String lastMessagePreview,
            Long lastMessageSeq,
            LocalDateTime lastMessageAt,
            List<String> profileImageKeys
    ) {
        this.eventType = eventType;
        this.roomId = roomId;
        this.receiverUserId = receiverUserId;
        this.receiverUserIds = receiverUserIds;
        this.roomName = roomName;
        this.memberCount = memberCount;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageSeq = lastMessageSeq;
        this.lastMessageAt = lastMessageAt;
        this.profileImageKeys = profileImageKeys;
    }

    public static ChatRoomListEvent roomAdded(
            Long roomId,
            Long receiverUserId,
            String roomName,
            Long memberCount,
            List<String> profileImageKeys
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.ROOM_ADDED,
                roomId,
                receiverUserId,
                null,
                roomName,
                memberCount,
                null,
                null,
                null,
                profileImageKeys
        );
    }

    public static ChatRoomListEvent roomRemoved(
            Long roomId,
            Long receiverUserId
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.ROOM_REMOVED,
                roomId,
                receiverUserId,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ChatRoomListEvent roomChanged(
            Long roomId,
            Long receiverUserId,
            String roomName,
            Long memberCount,
            List<String> profileImageKeys
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.ROOM_CHANGED,
                roomId,
                receiverUserId,
                null,
                roomName,
                memberCount,
                null,
                null,
                null,
                profileImageKeys
        );
    }

    public static ChatRoomListEvent messageSent(
            Long roomId,
            List<Long> receiverUserIds,
            String lastMessagePreview,
            Long lastMessageSeq,
            LocalDateTime lastMessageAt
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.MESSAGE_SENT,
                roomId,
                null,
                receiverUserIds,
                null,
                null,
                lastMessagePreview,
                lastMessageSeq,
                lastMessageAt,
                null
        );
    }
}
