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
	DUPLICATE_USERNAME_ERROR(HttpStatus.CONFLICT, "U002", "중복된 유저명 입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U003", "존재하지 않는 유저입니다."),
	INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "U004", "비밀번호가 일치하지 않습니다."),
	FORBIDDEN_USER_ACCESS(HttpStatus.FORBIDDEN, "U005", "해당 사용자 정보에 접근할 권한이 없습니다."),
	
    // JWT 토큰 관련
    JWT_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "J001", "유효하지 않은 JWT 토큰입니다."),
    JWT_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "J002", "만료된 JWT 토큰입니다."),
    JWT_UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "J003", "지원되지 않는 JWT 토큰 형식입니다."),
    JWT_EMPTY_TOKEN(HttpStatus.UNAUTHORIZED, "J004", "JWT 토큰이 비어있습니다."),
	JWT_MISSING_AUTH_HEADER(HttpStatus.UNAUTHORIZED, "J005", "Authorization 헤더가 존재하지 않습니다."),
	JWT_INVALID_AUTH_SCHEME(HttpStatus.UNAUTHORIZED, "J006", "Authorization 헤더가 Bearer 형식이 아닙니다."),
	JWT_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "J007", "유효하지 않은 리프레시 토큰입니다."),
	ACCESS_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "J008", "현재 사용할 수 없는 액세스 토큰입니다."),
	
	// 친구(Friend)
	ALREADY_FRIEND(HttpStatus.CONFLICT, "F001", "이미 친구로 등록되어 있습니다."),
	SELF_FRIEND_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "F002", "자기 자신을 친구로 추가할 수 없습니다."),

    // 채팅방
    DIRECT_CHAT_ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "CR001", "이미 존재하는 1대1 채팅방 입니다."),
    CHAT_ROOM_USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "CR002", "이미 채팅방에 존재하는 유저입니다."),
    CANNOT_CREATE_CHAT_ROOM_WITH_SELF(HttpStatus.BAD_REQUEST, "CR003", "자기 자신과 채팅방을 만들 수 없습니다."),
    CHAT_ROOM_MEMBER_REQUIRED(HttpStatus.BAD_REQUEST, "CR004", "단체 채팅은 2명 이상이 필요합니다."),
    CANNOT_CREATE_CHAT_ROOM_WITH_NON_FRIEND(HttpStatus.BAD_REQUEST, "CR005", "친구가 아닌 사용자와는 채팅방을 생성할 수 없습니다.");

	private ErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}
	
	private final HttpStatus status;
	private final String code;
	private final String message;
}
