package com.tgg.chat.domain.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginResponseDto;
import com.tgg.chat.domain.auth.service.AuthService;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.service.UserService;
import com.tgg.chat.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth API", description = "로그인, 로그아웃, 토큰 재발급 등의 인증 관련 API")
@RestController
@RequiredArgsConstructor
public class AuthController {
	
	private final AuthService authService;
	
	@PostMapping("/login")
	@Operation(
			summary = "로그인", 
			description = "이메일과 비밀번호로 로그인 합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "로그인 성공",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = LoginResponseDto.class)
				)
		),
		@ApiResponse(
				responseCode = "400", 
				description = "잘못된 요청",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ErrorResponse.class)
				)
		),
		@ApiResponse(
				responseCode = "401", 
				description = "잘못된 비밀번호",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ErrorResponse.class)
				)
		),
		@ApiResponse(
				responseCode = "404", 
				description = "존재하지 않는 유저",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ErrorResponse.class)
				)
		)
	})
	public ResponseEntity<LoginResponseDto> login(@RequestBody @Valid LoginRequestDto loginRequestDto) {
		
		LoginResponseDto loginResponseDto = authService.login(loginRequestDto);
		
		return ResponseEntity.status(HttpStatus.OK).body(loginResponseDto);
		
	}
	
}
