package com.tgg.chat.domain.chat.dto.query;

import java.time.LocalDateTime;

import com.tgg.chat.domain.chat.enums.ChatMessageType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class ChatMessageListRowDto {
	private Long unreadCount;
	
    private Long seq;
	
    private ChatMessageType chatMessageType;

    private String content;

    private LocalDateTime createdAt;
}
