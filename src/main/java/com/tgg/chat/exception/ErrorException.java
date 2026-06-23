package com.tgg.chat.exception;

import lombok.Getter;

@Getter
public class ErrorException extends RuntimeException {
	private final ErrorCode errorCode;
	
	public ErrorException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public ErrorException(ErrorCode errorCode, Throwable throwable) {
        super(errorCode.getMessage(), throwable);
        this.errorCode = errorCode;
    }
}
