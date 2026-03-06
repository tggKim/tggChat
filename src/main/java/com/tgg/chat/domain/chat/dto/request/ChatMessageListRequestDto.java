package com.tgg.chat.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "채팅 메시지 리스트 요청 DTO")
public class ChatMessageListRequestDto {
    @Schema(description = "채팅방 Id", example = "1")
    @NotNull(message = "chatRoomId 은 필수입니다.")
    private Long chatRoomId;
    
    @Schema(description = "메시지 조회 기준 offset, 기본값은 0 전달", example = "1")
    @NotNull(message = "offsetSeq 은 필수입니다.")
    private Long offsetSeq;
}
