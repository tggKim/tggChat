package com.tgg.chat.common.messaging.stomp;

import java.security.Principal;

import com.tgg.chat.common.messaging.principal.StompPrincipal;
import com.tgg.chat.common.security.jwt.AccessTokenAuthenticator;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final AccessTokenAuthenticator accessTokenAuthenticator;
	
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(StompCommand.CONNECT.equals(accessor.getCommand())) {
            String bearerString = accessor.getFirstNativeHeader("Authorization");
            AuthenticatedUser authenticatedUser = accessTokenAuthenticator.authenticateBearerToken(bearerString);

            Principal stompPrincipal = new StompPrincipal(authenticatedUser.getUserId());
            accessor.setUser(stompPrincipal);
        }

        return message;
    }
}
