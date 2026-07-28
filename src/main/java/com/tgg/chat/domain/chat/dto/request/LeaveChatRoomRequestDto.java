package com.tgg.chat.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "채팅방 나가기 요청 DTO")
public class LeaveChatRoomRequestDto {
    @Schema(description = "방장 권한 양도할 유저 id 없다면 생략해도 된다", example = "1")
    private Long nextOwnerId;
}
