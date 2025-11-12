package com.tgg.chat.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(ErrorException.class)
	protected ResponseEntity<ErrorResponse> handleErrorException(ErrorException e) {
		
		ErrorCode errorCode = e.getErrorCode();
		
		String errorCodeMessage = errorCode.getMessage();
		String errorMessage = e.getMessage();
		
        log.warn("[ErrorException] code={}, status={}, message={}",
                errorCode.getCode(),
                errorCode.getStatus().value(),
                errorMessage);
        
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
	
	@ExceptionHandler(MethodArgumentNotValidException.class)
	protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
		
		String errorMessage = e.getBindingResult()
			    .getFieldErrors()
			    .stream()
			    .findFirst()
			    .map(FieldError::getDefaultMessage)
			    .orElse("입력값이 올바르지 않습니다.");
		
		ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
		
        log.warn("[ValidationError] code={}, status={}, message={}",
                errorCode.getCode(),
                errorCode.getStatus().value(),
                errorMessage);
        
        return ResponseEntity
        		.status(errorCode.getStatus())
        		.body(ErrorResponse.of(errorCode, errorMessage));
		
	}

}
