package com.tgg.chat.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "채팅방 나가기 요청 DTO")
public class LeaveChatRoomRequestDto {

    @Schema(description = "채팅방 id", example = "1")
    @NotNull(message = "chatRoomId는 필수입니다.")
    private Long chatRoomId;
    
    @Schema(description = "방장 권한 양도할 유저 id, 일반유저가 나가기 요청시 0으로 보낸다.", example = "1")
    @NotNull(message = "nextOwnerId는 필수입니다.")
    private Long nextOwnerId;

}
