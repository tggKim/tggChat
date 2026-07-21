package com.tgg.chat.domain.chat.dto.response;

import java.time.LocalDateTime;

import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.enums.ChatMessageType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "채팅방 메시지 리스트 응답 DTO")
public class ChatMessageListResponseDto {
    @Schema(description = "메시지 id", example = "1")
    private final Long messageId;
	
    @Schema(description = "채팅 메시지 타입", example = "DIRECT")
    private final ChatMessageType chatMessageType;

    @Schema(description = "채팅 메시지 내용", example = "채팅방1")
    private final String content;

    @Schema(description = "메시지 보낸 유저의 id")
    private final Long senderId;

    @Schema(description = "메시지 보낸 유저의 유저명")
    private final String senderName;

    @Schema(description = "메시지 보낸 유저의 프로필 이미지 키")
    private final String senderProfileImageKey;

    @Schema(description = "채팅 메시지 생성 시각")
    private final LocalDateTime createdAt;

    private ChatMessageListResponseDto(
            Long messageId,
            ChatMessageType chatMessageType,
            String content,
            Long senderId,
            String senderName,
            String senderProfileImageKey,
            LocalDateTime createdAt
    ) {
        this.messageId = messageId;
        this.chatMessageType = chatMessageType;
        this.content = content;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderProfileImageKey = senderProfileImageKey;
        this.createdAt = createdAt;
    }

    public static ChatMessageListResponseDto from(ChatMessage chatMessage) {
        return new ChatMessageListResponseDto(
                chatMessage.getChatMessageId(),
                chatMessage.getChatMessageType(),
                chatMessage.getContent(),
                chatMessage.getSender().getDeleted() ? null : chatMessage.getSender().getUserId(),
                chatMessage.getSender().getDeleted() ? null : chatMessage.getSender().getUsername(),
                chatMessage.getSender().getDeleted() ? null : chatMessage.getSender().getProfileImageKey(),
                chatMessage.getCreatedAt()
        );
    }
}
