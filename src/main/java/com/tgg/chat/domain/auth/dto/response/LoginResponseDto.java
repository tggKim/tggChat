package com.tgg.chat.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "로그인 응답 DTO")
public class LoginResponseDto {
	
	@Schema(description = "엑세스 토큰")
	private final String accessToken;
	
	@Schema(description = "리프레시 토큰")
	private final String refreshToken;

	private LoginResponseDto(String accessToken, String refreshToken) {	
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
	}
	
	public static LoginResponseDto of(String accessToken, String resfreshToken) {
		return new LoginResponseDto(accessToken, resfreshToken);
	}
	
}
