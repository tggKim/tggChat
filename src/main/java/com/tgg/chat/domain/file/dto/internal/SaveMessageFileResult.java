package com.tgg.chat.domain.file.dto.internal;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class SaveMessageFileResult {
    private List<ChatRoomListEvent> chatRoomListEvents;
    private ChatEvent chatEvent;

    private SaveMessageFileResult(List<ChatRoomListEvent> chatRoomListEvents, ChatEvent chatEvent) {
        this.chatRoomListEvents = chatRoomListEvents;
        this.chatEvent = chatEvent;
    }

    public static SaveMessageFileResult of(List<ChatRoomListEvent> chatRoomListEvents, ChatEvent chatEvent) {
        return new SaveMessageFileResult(chatRoomListEvents, chatEvent);
    }
}
