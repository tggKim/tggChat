package com.tgg.chat.common.stomp;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.tgg.chat.common.jwt.JwtUtils;
import com.tgg.chat.common.redis.RedisUtils;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

	private final JwtUtils jwtUtils;
	private final RedisUtils redisUtils;
	
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

			jwtUtils.validateToken(jwtString);
			
			// claims 추출
			Claims claims = jwtUtils.getClaims(jwtString);
			
			// 레디스에 저장된 accessToken 과 비교
			Long userId = Long.parseLong(claims.getSubject());
			String redisAccessToken = redisUtils.getAccessToken(userId);
			if(redisAccessToken == null || !redisAccessToken.equals(jwtString)) {
				throw new ErrorException(ErrorCode.ACCESS_TOKEN_MISMATCH);
			}

            System.out.println(claims);

        }

        return message;

    }
}
