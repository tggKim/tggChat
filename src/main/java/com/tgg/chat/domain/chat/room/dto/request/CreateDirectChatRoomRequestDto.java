package com.tgg.chat.domain.chat.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "1대1 채팅방 생성 요청 DTO")
public class CreateDirectChatRoomRequestDto {

    @Schema(description = "채팅방을 만들 상대 userId", example = "tgg")
    private Long friendId;

}
