package com.tgg.chat.common.messaging.stomp;

import java.security.Principal;

import com.tgg.chat.common.messaging.principal.StompPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

	private final JwtUtils jwtUtils;
	private final RedisTokenStore redisTokenStore;
	
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(StompCommand.CONNECT.equals(accessor.getCommand())) {

            Object tokenObject = accessor.getFirstNativeHeader("Authorization");
            
            if(tokenObject == null) {
        		throw new ErrorException(ErrorCode.JWT_MISSING_AUTH_HEADER);
        	}
        	
        	String bearerString = tokenObject.toString();
        	
        	if(!bearerString.startsWith("Bearer ")) {
				throw new ErrorException(ErrorCode.JWT_INVALID_AUTH_SCHEME);
			}
        	
			String jwtString = bearerString.substring(7);

			// claims 추출
			Claims claims = jwtUtils.parseClaims(jwtString);
			
			// 레디스에 저장된 accessToken 과 비교
			Long userId = Long.parseLong(claims.getSubject());
			String redisAccessToken = redisTokenStore.getAccessToken(userId);
			if(redisAccessToken == null || !redisAccessToken.equals(jwtString)) {
				throw new ErrorException(ErrorCode.ACCESS_TOKEN_MISMATCH);
			}

            Principal stomPrincipal = new StompPrincipal(userId);
            accessor.setUser(stomPrincipal);
            
        }

        return message;

    }
}
