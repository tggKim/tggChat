package com.tgg.chat.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "로그인 응답 DTO")
public class LoginResponseDto {
	
	@Schema(description = "엑세스 토큰")
	private final String accessToken;

	private LoginResponseDto(String accessToken) {
		this.accessToken = accessToken;
	}
	
	public static LoginResponseDto of(String accessToken) {
		return new LoginResponseDto(accessToken);
	}
	
}
