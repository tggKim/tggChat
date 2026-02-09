package com.tgg.chat.domain.chat.dto.query;

import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import lombok.Getter;

@Getter
public class ChatRoomUserStatusRowDto {

    private Long userId;

    private ChatRoomUserStatus chatRoomUserStatus;

}
