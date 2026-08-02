package com.tgg.chat.domain.chat.dto.query;

import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ChatRoomListBaseRowDto {
    private Long roomId;
    private ChatRoomType roomType;
    private String baseRoomName;
    private String customRoomName;
    private ChatRoomUserRole myRole;
    private LocalDateTime joinedAt;
    private Long unreadStartMessageId;
}
