package com.tgg.chat.common.messaging.event;

import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ChatRoomListEvent {
    private final ChatRoomListEventType eventType;

    private final Long roomId;
    private final ChatRoomType roomType;
    private final Long receiverUserId;

    private final String baseRoomName;
    private final String customRoomName;
    private final ChatRoomUserRole myRole;

    private final Long memberCount;
    private final List<ChatRoomPreviewUser> previewUsers;

    private final String lastMessagePreview;
    private final Long messageId;
    private final LocalDateTime lastActivityAt;

    // 해당 messageId를 포함한 메시지부터 읽지 않은 상태
    private final Long unreadStartMessageId;
    private final Long unreadCount;

    private ChatRoomListEvent(
            ChatRoomListEventType eventType,
            Long roomId,
            ChatRoomType roomType,
            Long receiverUserId,
            String baseRoomName,
            String customRoomName,
            ChatRoomUserRole myRole,
            Long memberCount,
            List<ChatRoomPreviewUser> previewUsers,
            String lastMessagePreview,
            Long messageId,
            LocalDateTime lastActivityAt,
            Long unreadStartMessageId,
            Long unreadCount
    ) {
        this.eventType = eventType;
        this.roomId = roomId;
        this.roomType = roomType;
        this.receiverUserId = receiverUserId;
        this.baseRoomName = baseRoomName;
        this.customRoomName = customRoomName;
        this.myRole = myRole;
        this.memberCount = memberCount;
        this.previewUsers = previewUsers;
        this.lastMessagePreview = lastMessagePreview;
        this.messageId = messageId;
        this.lastActivityAt = lastActivityAt;
        this.unreadStartMessageId = unreadStartMessageId;
        this.unreadCount = unreadCount;
    }

    public static ChatRoomListEvent roomAdded(
            Long roomId,
            ChatRoomType roomType,
            Long receiverUserId,
            String baseRoomName,
            String customRoomName,
            ChatRoomUserRole myRole,
            Long memberCount,
            List<ChatRoomPreviewUser> previewUsers,
            String lastMessagePreview,
            Long messageId,
            LocalDateTime lastActivityAt,
            Long unreadStartMessageId,
            Long unreadCount
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.ROOM_ADDED,
                roomId,
                roomType,
                receiverUserId,
                baseRoomName,
                customRoomName,
                myRole,
                memberCount,
                previewUsers,
                lastMessagePreview,
                messageId,
                lastActivityAt,
                unreadStartMessageId,
                unreadCount
        );
    }

    public static ChatRoomListEvent roomChanged(
            Long roomId,
            ChatRoomType roomType,
            Long receiverUserId,
            String baseRoomName,
            String customRoomName,
            ChatRoomUserRole myRole,
            Long memberCount,
            List<ChatRoomPreviewUser> previewUsers,
            String lastMessagePreview,
            Long messageId,
            LocalDateTime lastActivityAt
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.ROOM_CHANGED,
                roomId,
                roomType,
                receiverUserId,
                baseRoomName,
                customRoomName,
                myRole,
                memberCount,
                previewUsers,
                lastMessagePreview,
                messageId,
                lastActivityAt,
                null,
                null
        );
    }

    public static ChatRoomListEvent roomNameChanged(
            Long roomId,
            Long receiverUserId,
            String baseRoomName,
            String customRoomName
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.ROOM_NAME_CHANGED,
                roomId,
                null,
                receiverUserId,
                baseRoomName,
                customRoomName,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ChatRoomListEvent roomRemoved(
            Long roomId,
            Long receiverUserId
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.ROOM_REMOVED,
                roomId,
                null,
                receiverUserId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ChatRoomListEvent messageSent(
            Long roomId,
            String lastMessagePreview,
            Long messageId,
            LocalDateTime lastActivityAt
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.MESSAGE_SENT,
                roomId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                lastMessagePreview,
                messageId,
                lastActivityAt,
                null,
                null
        );
    }

    public static ChatRoomListEvent messageRead(
            Long roomId,
            Long receiverUserId,
            Long unreadStartMessageId,
            Long unreadCount
    ) {
        return new ChatRoomListEvent(
                ChatRoomListEventType.MESSAGE_READ,
                roomId,
                null,
                receiverUserId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                unreadStartMessageId,
                unreadCount
        );
    }
}
