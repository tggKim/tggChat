package com.tgg.chat.domain.file.dto.internal;

import com.tgg.chat.common.messaging.event.UserMetadataEvent;
import lombok.Getter;

@Getter
public class SaveUserProfileResult {
    private UserMetadataEvent userMetadataEvent;
    private String previousProfileImageKey;

    private SaveUserProfileResult(UserMetadataEvent userMetadataEvent, String previousProfileImageKey) {
        this.userMetadataEvent = userMetadataEvent;
        this.previousProfileImageKey = previousProfileImageKey;
    }

    public static SaveUserProfileResult of(UserMetadataEvent userMetadataEvent, String previousProfileImageKey) {
        return new SaveUserProfileResult(userMetadataEvent, previousProfileImageKey);
    }
}
