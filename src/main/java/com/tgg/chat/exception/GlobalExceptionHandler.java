package com.tgg.chat.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(ErrorException.class)
	protected ResponseEntity<ErrorResponse> handleErrorException(ErrorException e) {
		
		ErrorCode errorCode = e.getErrorCode();
		
        log.warn("[ErrorException] code={}, status={}, message={}",
                errorCode.getCode(),
                errorCode.getStatus(),
                e.getMessage());
		
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
		
	}
	
	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ErrorResponse> handleException(Exception e) {
		
		log.error("[Unhandled Exception]", e);
		
		return ResponseEntity
				.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
				.body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
		
	}

}
