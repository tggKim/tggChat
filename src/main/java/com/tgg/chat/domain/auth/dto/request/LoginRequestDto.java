package com.tgg.chat.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "로그인 요청 DTO")
public class LoginRequestDto {

	@Email(message = "올바른 이메일 형식이 아닙니다.")
	@NotBlank(message = "이메일은 필수입니다.")
	@Size(max = 254, message = "이메일 길이는 254자 이하입니다.")
	@Schema(description = "이메일", example = "test@example.com")
	private String email;
	
	@NotBlank(message = "비밀번호는 필수입니다.")
	@Schema(description = "비밀번호", example = "1234567")
	private String password;
	
}
