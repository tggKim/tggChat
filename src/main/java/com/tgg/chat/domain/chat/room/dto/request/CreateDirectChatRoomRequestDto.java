package com.tgg.chat.domain.chat.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "1대1 채팅방 생성 요청 DTO")
public class CreateDirectChatRoomRequestDto {

    @Schema(description = "채팅방을 만들 상대 userId", example = "1")
    @NotNull(message = "friendId 는 필수입니다.")
    private Long friendId;

}
