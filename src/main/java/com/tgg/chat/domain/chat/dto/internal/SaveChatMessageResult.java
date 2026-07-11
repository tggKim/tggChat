package com.tgg.chat.domain.chat.dto.internal;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class SaveChatMessageResult {
    private final List<ChatEvent> chatEvents;
    private final List<ChatRoomListEvent> chatRoomListEvents;

    private SaveChatMessageResult(
            List<ChatEvent> chatEvents,
            List<ChatRoomListEvent> chatRoomListEvents
    ) {
        this.chatEvents = chatEvents;
        this.chatRoomListEvents = chatRoomListEvents;
    }

    public static SaveChatMessageResult of(
            List<ChatEvent> chatEvents,
            List<ChatRoomListEvent> chatRoomListEvents
    ) {
        return new SaveChatMessageResult(chatEvents, chatRoomListEvents);
    }
}
