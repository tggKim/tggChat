package com.tgg.chat.domain.chat.dto.internal;

import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import com.tgg.chat.domain.chat.dto.response.CreateDirectChatRoomResponseDto;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateDirectChatRoomResult {
    private final CreateDirectChatRoomResponseDto responseDto;
    private final List<ChatRoomListEvent> chatRoomListEvents;

    private CreateDirectChatRoomResult(
            CreateDirectChatRoomResponseDto responseDto,
            List<ChatRoomListEvent> chatRoomListEvents
    ) {
        this.responseDto = responseDto;
        this.chatRoomListEvents = chatRoomListEvents;
    }

    public static CreateDirectChatRoomResult of(
            CreateDirectChatRoomResponseDto responseDto,
            List<ChatRoomListEvent> chatRoomListEvents
    ) {
        return new CreateDirectChatRoomResult(responseDto, chatRoomListEvents);
    }
}