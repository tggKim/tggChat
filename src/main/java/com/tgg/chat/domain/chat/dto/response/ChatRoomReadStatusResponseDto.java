package com.tgg.chat.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "채팅방 유저별 읽음 범위 조회 응답 DTO")
public class ChatRoomReadStatusResponseDto {
    @Schema(description = "유저 ID", example = "1")
    private final Long userId;

    @Schema(description = "유저가 읽지 않은 메시지의 시작점", example = "1")
    private final Long unreadStartMessageId;

    private ChatRoomReadStatusResponseDto(Long userId, Long unreadStartMessageId) {
        this.userId = userId;
        this.unreadStartMessageId = unreadStartMessageId;
    }

    public static ChatRoomReadStatusResponseDto of(Long userId, Long unreadStartMessageId) {
        return new ChatRoomReadStatusResponseDto(userId, unreadStartMessageId);
    }
}
