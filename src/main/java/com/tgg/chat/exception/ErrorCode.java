package com.tgg.chat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {
	
	// 유저
	NOT_VALID_EMAIL_ERROR(HttpStatus.BAD_REQUEST, "U001", "올바르지 않은 이메일 형식 입니다."),
	DUPLICATE_EMAIL_ERROR(HttpStatus.CONFLICT, "U002", "중복된 이메일 입니다.");
	
	private ErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}
	
	private final HttpStatus status;
	private final String code;
	private final String message;
}
