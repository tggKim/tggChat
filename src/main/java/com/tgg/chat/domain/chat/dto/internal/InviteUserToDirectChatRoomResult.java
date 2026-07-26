package com.tgg.chat.domain.chat.dto.internal;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class InviteUserToDirectChatRoomResult {
    private final List<ChatRoomListEvent> chatRoomListEvents;
    private final ChatEvent chatEvent;

    private InviteUserToDirectChatRoomResult(List<ChatRoomListEvent> chatRoomListEvents, ChatEvent chatEvent) {
        this.chatRoomListEvents = chatRoomListEvents;
        this.chatEvent = chatEvent;
    }

    public static InviteUserToDirectChatRoomResult of(List<ChatRoomListEvent> chatRoomListEvents, ChatEvent chatEvent) {
        return new InviteUserToDirectChatRoomResult(chatRoomListEvents, chatEvent);
    }
}
