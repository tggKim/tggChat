package com.tgg.chat.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequestDto {
	
	private String email;
	
	private String password;
	
	private String username;

}
