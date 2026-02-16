package com.tgg.chat.domain.chat.dto.internal;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import lombok.Getter;

import java.util.List;

@Getter
public class ChatEventResult {
    private List<ChatEvent> chatEvents;
    private Long lastSeq;
    private ChatMessage flagChatMessage;

    private ChatEventResult(List<ChatEvent> chatEvents, Long lastSeq, ChatMessage flagChatMessage) {
        this.chatEvents = chatEvents;
        this.lastSeq = lastSeq;
        this.flagChatMessage = flagChatMessage;
    }

    public static ChatEventResult of(List<ChatEvent> chatEvents, Long lastSeq, ChatMessage flagChatMessage) {
        return new ChatEventResult(chatEvents, lastSeq, flagChatMessage);
    }
}
