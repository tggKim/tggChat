package com.tgg.chat.domain.chat.dto.query;

import lombok.Getter;

@Getter
public class ChatRoomUnreadCountRowDto {
    private Long roomId;
    private Long unreadCount;
}
