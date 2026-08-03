package com.tgg.chat.domain.chat.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReadChatMessagesRequestDto {
    private Long readMessageId;
}
