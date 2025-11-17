package com.tgg.chat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {
	
	// 공통(Common)
	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "요청 값이 유효하지 않습니다."),
	
	// 서버 에러(Server)
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S001", "서버 내부 오류가 발생했습니다."),
	
	// 유저(User)
	DUPLICATE_EMAIL_ERROR(HttpStatus.CONFLICT, "U001", "중복된 이메일 입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "존재하지 않는 유저입니다."),
	
    // JWT 토큰 관련
    JWT_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "J001", "유효하지 않은 JWT 토큰입니다."),
    JWT_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "J002", "만료된 JWT 토큰입니다."),
    JWT_UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "J003", "지원되지 않는 JWT 토큰 형식입니다."),
    JWT_EMPTY_TOKEN(HttpStatus.UNAUTHORIZED, "J004", "JWT 토큰이 비어있습니다.");
	
	private ErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}
	
	private final HttpStatus status;
	private final String code;
	private final String message;
}
