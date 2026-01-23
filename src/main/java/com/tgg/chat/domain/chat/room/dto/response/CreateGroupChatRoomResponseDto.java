package com.tgg.chat.domain.chat.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description =  "단체 채팅방 생성 응답 DTO")
public class CreateGroupChatRoomResponseDto {
	
    @Schema(description = "만들어진 채팅방 id", example = "1")
    private final Long chatRoomId;

    private CreateGroupChatRoomResponseDto(Long chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public static CreateGroupChatRoomResponseDto of(Long chatRoomId) {
        return new CreateGroupChatRoomResponseDto(chatRoomId);
    }

}
