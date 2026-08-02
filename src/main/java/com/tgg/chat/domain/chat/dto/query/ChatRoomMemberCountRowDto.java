package com.tgg.chat.domain.chat.dto.query;

import lombok.Getter;

@Getter
public class ChatRoomMemberCountRowDto {
    private Long roomId;
    private Long memberCount;
}
