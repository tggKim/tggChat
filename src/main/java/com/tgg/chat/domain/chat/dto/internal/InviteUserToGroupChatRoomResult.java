package com.tgg.chat.domain.chat.dto.internal;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class InviteUserToGroupChatRoomResult {
    private final List<ChatRoomListEvent> chatRoomListEvents;
    private final ChatEvent chatEvent;

    private InviteUserToGroupChatRoomResult(List<ChatRoomListEvent> chatRoomListEvents, ChatEvent chatEvent) {
        this.chatRoomListEvents = chatRoomListEvents;
        this.chatEvent = chatEvent;
    }

    public static InviteUserToGroupChatRoomResult of(List<ChatRoomListEvent> chatRoomListEvents, ChatEvent chatEvent) {
        return new InviteUserToGroupChatRoomResult(chatRoomListEvents, chatEvent);
    }
}
