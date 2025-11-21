package com.tgg.chat.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "로그인 여부 응답 DTO")
public class LoginStatusResponseDto {

	@Schema(description = "로그인 여부 플래그")
	private final Boolean isLoggedIn;
	
	private LoginStatusResponseDto(Boolean isLoggedIn) {
		this.isLoggedIn = isLoggedIn;
	}
	
	public static LoginStatusResponseDto of(Boolean isLoggedIn) {
		return new LoginStatusResponseDto(isLoggedIn);
	}
	
}
