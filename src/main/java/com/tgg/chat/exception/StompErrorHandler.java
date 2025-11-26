package com.tgg.chat.exception;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompErrorHandler extends StompSubProtocolErrorHandler{
	
	private final ObjectMapper objectMapper;
	
	@Override
	public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
		
		if(ex.getCause() instanceof ErrorException errorException) {
			
			ErrorCode errorCode = errorException.getErrorCode();
			String errorMessage = errorCode.getMessage();
			
	        log.warn("[ErrorException] code={}, status={}, message={}",
	                errorCode.getCode(),
	                errorCode.getStatus().value(),
	                errorMessage);
	        
	        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
	        
	        byte[] errorResponseByte = null;
	        try {
	        	
				errorResponseByte = objectMapper.writeValueAsBytes(errorResponse);
				
			} catch (JsonProcessingException e) {
				
				ErrorCode internalServerError = ErrorCode.INTERNAL_SERVER_ERROR;
				String internalServerErrorMessage = internalServerError.getMessage();
				
		        log.error("[Unhandled Exception] code={}, status={}, message={}",
		        		internalServerError.getCode(),
		        		internalServerError.getStatus().value(),
		        		internalServerErrorMessage);
			    
		        String timestamp = LocalDateTime.now()
		                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		        
				String fallbackJson = String.format(
			            "{\"code\":\"%s\",\"status\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
			            internalServerError.getCode(),
			            internalServerError.getStatus().value(),
			            internalServerError.getMessage(),
			            timestamp
			    );
				
				errorResponseByte = fallbackJson.getBytes();
				
			}
		
			StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
			accessor.setLeaveMutable(true);
			
			Message<byte[]> message = MessageBuilder.createMessage(errorResponseByte, accessor.getMessageHeaders());

			return message;
			
		}
		
		return super.handleClientMessageProcessingError(clientMessage, ex);
	
	}
	
}
