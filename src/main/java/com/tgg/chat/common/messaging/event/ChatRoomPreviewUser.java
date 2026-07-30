package com.tgg.chat.common.messaging.event;

import lombok.Getter;

@Getter
public class ChatRoomPreviewUser {
    private final Long userId;
    private final String username;
    private final String profileImageKey;

    private ChatRoomPreviewUser(
            Long userId,
            String username,
            String profileImageKey
    ) {
        this.userId = userId;
        this.username = username;
        this.profileImageKey = profileImageKey;
    }

    public static ChatRoomPreviewUser of(
            Long userId,
            String username,
            String profileImageKey
    ) {
        return new ChatRoomPreviewUser(
                userId,
                username,
                profileImageKey
        );
    }
}
