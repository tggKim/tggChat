package com.tgg.chat.domain.chat.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description =  "1대1 채팅방 생성 응답 DTO")
public class CreateDirectChatRoomResponseDto {

    @Schema(description = "만들어진 채팅방 id", example = "1")
    private final Long chatRoomId;

    private CreateDirectChatRoomResponseDto(Long chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public static CreateDirectChatRoomResponseDto of(Long chatRoomId) {
        return new CreateDirectChatRoomResponseDto(chatRoomId);
    }

}
