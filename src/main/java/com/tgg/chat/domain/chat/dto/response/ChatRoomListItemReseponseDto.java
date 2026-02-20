package com.tgg.chat.domain.chat.dto.response;

import java.time.LocalDateTime;

import com.tgg.chat.domain.chat.dto.query.ChatRoomListRowDto;
import com.tgg.chat.domain.chat.enums.ChatRoomType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "채팅방 목록 조회의 개별 항목 응답 DTO")
public class ChatRoomListItemReseponseDto {
    @Schema(description = "채팅방 ID", example = "1")
    private final Long chatRoomId;

    @Schema(description = "채팅방 타입", example = "DIRECT")
    private final ChatRoomType chatRoomType;

    @Schema(description = "채팅방 이름", example = "채팅방1")
    private final String roomName;

    @Schema(description = "마지막 메시지 미리보기", example = "안녕하세요")
    private final String lastMessagePreview;

    @Schema(description = "마지막 메시지 시각")
    private final LocalDateTime lastMessageAt;

    @Schema(description = "읽지 않은 메시지 개수", example = "15")
    private final Long unreadCount;

    private ChatRoomListItemReseponseDto(
            Long chatRoomId,
            ChatRoomType chatRoomType,
            String roomName,
            String lastMessagePreview,
            LocalDateTime lastMessageAt,
            Long unreadCount
    ) {
        this.chatRoomId = chatRoomId;
        this.chatRoomType = chatRoomType;
        this.roomName = roomName;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
    }

    public static ChatRoomListItemReseponseDto from(ChatRoomListRowDto row) {
        return new ChatRoomListItemReseponseDto(
                row.getChatRoomId(),
                row.getChatRoomType(),
                row.getRoomName(),
                row.getLastMessagePreview(),
                row.getLastMessageAt(),
                row.getUnreadCount()
        );
    }
}
