package com.tgg.chat.domain.chat.dto.query;

import com.tgg.chat.domain.chat.enums.ChatRoomType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ChatRoomListRowDto {

    private Long chatRoomId;

    private ChatRoomType chatRoomType;

    private String roomName;

    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    private Long unreadCount;

}
