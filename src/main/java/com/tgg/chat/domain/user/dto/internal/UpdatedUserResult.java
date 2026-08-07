package com.tgg.chat.domain.user.dto.internal;

import com.tgg.chat.common.messaging.event.UserMetadataEvent;
import lombok.Getter;

@Getter
public class UpdatedUserResult {
    private final UserMetadataEvent userMetadataEvent;

    private UpdatedUserResult(UserMetadataEvent userMetadataEvent) {
        this.userMetadataEvent = userMetadataEvent;
    }

    public static UpdatedUserResult of(UserMetadataEvent userMetadataEvent) {
        return new UpdatedUserResult(userMetadataEvent);
    }
}
