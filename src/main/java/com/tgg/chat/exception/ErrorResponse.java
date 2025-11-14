package com.tgg.chat.exception;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "에러 응답 DTO")
public class ErrorResponse {
	
	@Schema(description = "에러 코드", example = "에러 코드")
	private final String code;
	
	@Schema(description = "HTTP 상태 코드", example = "HTTP 상태 코드", type = "string")
	private final int status;
	
	@Schema(description = "에러 메시지", example = "에러 메시지")
	private final String message;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Schema(description = "에러 발생 시각", example = "에러 발생 시각", type = "string")
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
