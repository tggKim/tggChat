package com.tgg.chat.domain.chat.room.dto.response;

import com.tgg.chat.domain.chat.room.dto.query.ChatRoomListRowDto;
import com.tgg.chat.domain.chat.room.enums.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "채팅방 리스트 응답 DTO")
public class ChatRoomListResponseDto {

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

    @Schema(description = "마지막 메시지 시퀀스", example = "15")
    private final Long lastMessageSeq;

    @Schema(description = "사용자가 읽은 마지막 메시지 시퀀스", example = "12")
    private final Long lastReadSeq;

    private ChatRoomListResponseDto(
            Long chatRoomId,
            ChatRoomType chatRoomType,
            String roomName,
            String lastMessagePreview,
            LocalDateTime lastMessageAt,
            Long lastMessageSeq,
            Long lastReadSeq
    ) {
        this.chatRoomId = chatRoomId;
        this.chatRoomType = chatRoomType;
        this.roomName = roomName;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageAt = lastMessageAt;
        this.lastMessageSeq = lastMessageSeq;
        this.lastReadSeq = lastReadSeq;
    }

    public static ChatRoomListResponseDto from(ChatRoomListRowDto row) {
        return new ChatRoomListResponseDto(
                row.getChatRoomId(),
                row.getChatRoomType(),
                row.getRoomName(),
                row.getLastMessagePreview(),
                row.getLastMessageAt(),
                row.getLastMessageSeq(),
                row.getLastReadSeq()
        );
    }
}
