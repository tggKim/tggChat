package com.tgg.chat.domain.chat.dto.internal;

import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class UpdateRoomNameResult {
    private final List<ChatRoomListEvent> chatRoomListEvents;

    private UpdateRoomNameResult(
            List<ChatRoomListEvent> chatRoomListEvents
    ) {
        this.chatRoomListEvents = chatRoomListEvents;
    }

    public static UpdateRoomNameResult of(
            List<ChatRoomListEvent> chatRoomListEvents
    ) {
        return new UpdateRoomNameResult(chatRoomListEvents);
    }
}
