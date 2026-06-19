package com.tgg.chat.common.messaging.stomp;

import com.tgg.chat.domain.chat.service.ChatRoomSubscriptionService;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import com.tgg.chat.exception.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ChatRoomSubscriptionInterceptor implements ChannelInterceptor {
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final ChatRoomSubscriptionService chatRoomSubscriptionService;
    private static final Pattern CHAT_ROOM_TOPIC_PATTERN = Pattern.compile("^/topic/chatRooms/(\\d+)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if(destination == null) {
            return message;
        }

        Matcher matcher = CHAT_ROOM_TOPIC_PATTERN.matcher(destination);
        if(!matcher.matches()) {
            return message;
        }

        Principal principal = accessor.getUser();
        if(principal == null) {
            throw new ErrorException(ErrorCode.WEBSOCKET_UNAUTHENTICATED);
        }

        Long userId = Long.parseLong(principal.getName());
        Long chatRoomId = Long.parseLong(matcher.group(1));

        if(!chatRoomSubscriptionService.validateCanSubscribe(userId, chatRoomId)) {
            messagingTemplateProvider.getObject().convertAndSendToUser(principal.getName(), "/queue/errors", ErrorResponse.of(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
            return null;
        }

        return message;
    }
}
