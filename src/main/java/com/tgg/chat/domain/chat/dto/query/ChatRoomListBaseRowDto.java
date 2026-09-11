package com.tgg.chat.domain.chat.dto.query;

import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;

import java.time.LocalDateTime;

public interface ChatRoomListBaseRowDto {
    Long getRoomId();
    ChatRoomType getRoomType();
    String getBaseRoomName();
    String getCustomRoomName();
    ChatRoomUserRole getMyRole();
    LocalDateTime getJoinedAt();
    Long getUnreadStartMessageId();
}