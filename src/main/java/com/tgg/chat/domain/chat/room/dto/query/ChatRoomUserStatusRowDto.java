package com.tgg.chat.domain.chat.room.dto.query;

import com.tgg.chat.domain.chat.room.enums.ChatRoomUserStatus;
import lombok.Getter;

@Getter
public class ChatRoomUserStatusRowDto {

    private Long userId;

    private ChatRoomUserStatus chatRoomUserStatus;

}
