package com.tgg.chat.common.config;

import com.tgg.chat.common.stomp.JwtChannelInterceptor;
import com.tgg.chat.exception.StompErrorHandler;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{

	private final JwtChannelInterceptor jwtChannelInterceptor;
	private final StompErrorHandler stompErrorHandler;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws") 			// 클라이언트가 서버와 최초로 연결하기 위한 엔드포인트 URL 지정(WebSocket 프로토콜로 전환하기 위한 핸드쉐이크가 일어나느 URL)
				.setAllowedOriginPatterns("*") 	// CORS 설정으로 어떤 도메인의 클라이언트가 서버와 연결 가능한지 지정
				.withSockJS(); 					// 브라우저가 WebSocket 미지원시 fallback 방식으로 동작하게 한다
		registry.setErrorHandler(stompErrorHandler);
	}
	
	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");					// 서버가 클라이언트들에게 메시지를 전달 할 때 사용하는 경로 접두어
		registry.setApplicationDestinationPrefixes("/app");		// 클라이언트가 서버로 메시지 보낼 때 사용하는 경로 접두어
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {

		registration.interceptors(jwtChannelInterceptor);

	}

}
