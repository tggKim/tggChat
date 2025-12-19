package com.tgg.chat.exception;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.xml.bind.annotation.XmlAccessOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompErrorHandler extends StompSubProtocolErrorHandler{
	
	private final ObjectMapper objectMapper;
	
	@Override
	public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
		
		Throwable stompError = ex.getCause();
		
		ErrorCode errorCode = null;
		
		// ErrorException 이면 그에 맞는 예외 처리, ErrorException 이 아니라면 서버에러(500) 리턴
		if(stompError instanceof ErrorException errorException) {
			
			errorCode = errorException.getErrorCode();
			
	        log.warn("[ErrorException] code={}, status={}, message={}",
	                errorCode.getCode(),
	                errorCode.getStatus().value(),
	                errorCode.getMessage());
			
		} else {
			
			errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
			
	        log.error("[UnhandledException] code={}, status={}, message={}",
	                errorCode.getCode(),
	                errorCode.getStatus().value(),
	                errorCode.getMessage());
			
		}
		
		ErrorResponse errorResponse = ErrorResponse.of(errorCode);
		byte[] errorResponseByte = errorResponseToByte(errorResponse);
		
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
		Message<byte[]> message = MessageBuilder.createMessage(errorResponseByte, accessor.getMessageHeaders());

		return message;
	
	}
	
	private byte[] errorResponseToByte(ErrorResponse errorResponse) {
		
		try {
			
			return objectMapper.writeValueAsBytes(errorResponse);
			
		} catch (JsonProcessingException e) {
			
			ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
			
	        log.error("[Unhandled Exception] code={}, status={}, message={}",
	        		errorCode.getCode(),
	        		errorCode.getStatus().value(),
	        		errorCode.getMessage());
		    
	        String timestamp = LocalDateTime.now()
	                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	        
			String fallbackJson = String.format(
		            "{\"code\":\"%s\",\"status\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
		            errorCode.getCode(),
		            errorCode.getStatus().value(),
		            errorCode.getMessage(),
		            timestamp);
			
			return fallbackJson.getBytes(StandardCharsets.UTF_8);
			
		}
		
	}
	
}
