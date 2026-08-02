package com.tgg.chat.domain.chat.dto.query;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ChatRoomLatestMessageRowDto {
    private Long roomId;
    private String lastMessagePreview;
    private Long messageId;
    private LocalDateTime createdAt;
}
