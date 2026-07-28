package com.tgg.chat.domain.chat.dto.internal;

import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class UpdateCustomRoomNameResult {
    private final List<ChatRoomListEvent> chatRoomListEvents;

    private UpdateCustomRoomNameResult(
            List<ChatRoomListEvent> chatRoomListEvents
    ) {
        this.chatRoomListEvents = chatRoomListEvents;
    }

    public static UpdateCustomRoomNameResult of(
            List<ChatRoomListEvent> chatRoomListEvents
    ) {
        return new UpdateCustomRoomNameResult(chatRoomListEvents);
    }
}
