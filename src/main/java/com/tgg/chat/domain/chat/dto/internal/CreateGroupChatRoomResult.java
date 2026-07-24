package com.tgg.chat.domain.chat.dto.internal;

import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import com.tgg.chat.domain.chat.dto.response.CreateGroupChatRoomResponseDto;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateGroupChatRoomResult {
    private final CreateGroupChatRoomResponseDto responseDto;
    private final List<ChatRoomListEvent> chatRoomListEvents;

    private CreateGroupChatRoomResult(CreateGroupChatRoomResponseDto responseDto, List<ChatRoomListEvent> chatRoomListEvents) {
        this.responseDto = responseDto;
        this.chatRoomListEvents = chatRoomListEvents;
    }

    public static CreateGroupChatRoomResult of(CreateGroupChatRoomResponseDto responseDto, List<ChatRoomListEvent> chatRoomListEvents) {
        return new CreateGroupChatRoomResult(responseDto, chatRoomListEvents);
    }
}
