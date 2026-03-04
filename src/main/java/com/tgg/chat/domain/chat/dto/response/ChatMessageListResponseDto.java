package com.tgg.chat.domain.chat.dto.response;

import java.time.LocalDateTime;

import com.tgg.chat.domain.chat.dto.query.ChatMessageListRowDto;
import com.tgg.chat.domain.chat.dto.query.ChatRoomListRowDto;
import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.chat.enums.ChatRoomType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "채팅방 메시지 리스트 응답 DTO")
public class ChatMessageListResponseDto {
    @Schema(description = "메시지 seq", example = "1")
    private final Long seq;
    
    @Schema(description = "읽지 않은 사람 수", example = "1")
    private final Long unReadCount;
	
    @Schema(description = "채팅 메시지 타입", example = "DIRECT")
    private final ChatMessageType chatMessageType;

    @Schema(description = "채팅 메시지 내용", example = "채팅방1")
    private final String content;

    @Schema(description = "채팅 메시지 생성 시각")
    private final LocalDateTime createdAt;

    private ChatMessageListResponseDto(
            Long seq,
            Long unReadCount,
            ChatMessageType chatMessageType,
            String content,
            LocalDateTime createdAt
    ) {
        this.seq = seq;
        this.unReadCount = unReadCount;
        this.chatMessageType = chatMessageType;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static ChatMessageListResponseDto from(ChatMessageListRowDto dto) {
        return new ChatMessageListResponseDto(
                dto.getSeq(),
                dto.getUnreadCount(),
                dto.getChatMessageType(),
                dto.getContent(),
                dto.getCreatedAt()
        );
    }
}
