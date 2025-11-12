package com.tgg.chat.exception;

import com.mysql.cj.jdbc.CloseOption;

public class ErrorException extends RuntimeException {
	
	private final ErrorCode errorCode;
	
	public ErrorException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}	
	
}
