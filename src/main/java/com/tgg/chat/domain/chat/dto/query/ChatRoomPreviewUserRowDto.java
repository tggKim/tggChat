package com.tgg.chat.domain.chat.dto.query;

import lombok.Getter;

@Getter
public class ChatRoomPreviewUserRowDto {
    private Long roomId;
    private Long userId;
    private String username;
    private String profileImageKey;
}
