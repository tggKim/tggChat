package com.tgg.chat.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "로그인 여부 응답 DTO")
public class LoginStatusResponse {

	@Schema(description = "로그인 여부 플래그")
	private final Boolean isLoggedIn;
	
	private LoginStatusResponse(Boolean isLoggedIn) {
		this.isLoggedIn = isLoggedIn;
	}
	
	public LoginStatusResponse of(Boolean isLoggedIn) {
		return new LoginStatusResponse(isLoggedIn);
	}
	
}
