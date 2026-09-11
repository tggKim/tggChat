package com.tgg.chat.domain.chat.dto.query;


import java.time.LocalDateTime;

public interface ChatRoomLatestMessageRowDto {
    Long getRoomId();
    String getLastMessagePreview();
    Long getMessageId();
    LocalDateTime getCreatedAt();
}
