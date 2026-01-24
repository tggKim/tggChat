package com.tgg.chat.domain.chat.room.dto.query;

import com.tgg.chat.domain.chat.room.enums.ChatRoomType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ChatRoomListRowDto {

    private Long chatRoomId;

    private ChatRoomType chatRoomType;

    private String roomName;

    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    private Long lastMessageSeq;

    private Long lastReadSeq;

}
