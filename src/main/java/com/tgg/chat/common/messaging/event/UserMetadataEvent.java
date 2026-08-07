package com.tgg.chat.common.messaging.event;

import lombok.Getter;

import java.util.List;

@Getter
public class UserMetadataEvent {
    private UserMetadataEventType userMetadataEventType;
    private Long userId;
    private String username;
    private String userProfileImageKey;
    private List<Long> eventUserIds;

    private UserMetadataEvent(UserMetadataEventType userMetadataEventType, Long userId, String username, String userProfileImageKey, List<Long> eventUserIds) {
        this.userMetadataEventType = userMetadataEventType;
        this.userId = userId;
        this.username = username;
        this.userProfileImageKey = userProfileImageKey;
        this.eventUserIds = eventUserIds;
    }

    public static UserMetadataEvent usernameUpdated(Long userId, String username, List<Long> eventUserIds) {
        return new UserMetadataEvent(
                UserMetadataEventType.USERNAME_UPDATED,
                userId,
                username,
                null,
                eventUserIds
        );
    }

    public static UserMetadataEvent userProfileImageUpdated(Long userId, String userProfileImageKey, List<Long> eventUserIds) {
        return new UserMetadataEvent(
                UserMetadataEventType.USER_PROFILE_IMAGE_UPDATE,
                userId,
                null,
                userProfileImageKey,
                eventUserIds
        );
    }

    public void clearEventUserIds() {
        this.eventUserIds = null;
    }
}
