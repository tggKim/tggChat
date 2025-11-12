package com.tgg.chat.exception;

import java.time.LocalDateTime;

import lombok.Getter;

@Getter
public class ErrorResponse {
	
	private final String code;
	private final int status;
	private final String message;
	private final LocalDateTime timestamp;
	
	private ErrorResponse(String code, int status, String message, LocalDateTime timestamp) {
		this.code = code;
		this.status = status;
		this.message = message;
		this.timestamp = timestamp;
	}
	
	public static ErrorResponse of(ErrorCode errorCode) {
		
		return new ErrorResponse(errorCode.getCode(),
									errorCode.getStatus().value(),
									errorCode.getMessage(),
									LocalDateTime.now());
		
		
	}
	
	public static ErrorResponse of(ErrorCode errorCode, String message) {
		
		return new ErrorResponse(errorCode.getCode(),
				errorCode.getStatus().value(),
				message,
				LocalDateTime.now());
		
	}
	
}
