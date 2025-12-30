package com.tgg.chat.domain.auth.controller;

import com.tgg.chat.domain.auth.dto.request.RefreshRequestDto;
import com.tgg.chat.domain.auth.dto.response.RefreshResponseDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.request.LoginStatusRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginResponseDto;
import com.tgg.chat.domain.auth.dto.response.LoginStatusResponseDto;
import com.tgg.chat.domain.auth.service.AuthService;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.service.UserService;
import com.tgg.chat.exception.ErrorResponse;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

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
		
        TokenPair tokenPair = authService.login(loginRequestDto);

        String accessToken = tokenPair.getAccessToken();
        String refreshToken = tokenPair.getRefreshToken();

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();

        LoginResponseDto loginResponseDto = LoginResponseDto.of(accessToken);
		
		return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(loginResponseDto);
		
	}
	
	@PostMapping("/login-status")
	@Operation(
			summary = "로그인 여부 확인", 
			description = "이메일을 통해서 로그인 여부를 확인합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "로그인 여부 확인 성공",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = LoginStatusResponseDto.class)
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
				responseCode = "404", 
				description = "존재하지 않는 유저",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ErrorResponse.class)
				)
		)
	})
	public ResponseEntity<LoginStatusResponseDto> isLoggedIn(@RequestBody @Valid LoginStatusRequestDto loginStatusRequestDto) {
		
		LoginStatusResponseDto loginStatusResponseDto = authService.isLoggedIn(loginStatusRequestDto);
		
		return ResponseEntity.status(HttpStatus.OK).body(loginStatusResponseDto);
		
	}
	
	@PostMapping("/logout")
	@SecurityRequirement(name = "JWT Auth")
	@Operation(
			summary = "로그아웃", 
			description = "accessToken을 통해서 로그아웃 요청 합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "로그아웃 성공",
				content = @Content(
					mediaType = "application/json"
				)
		),
		@ApiResponse(
				responseCode = "401",
				description = "유효하지 않은 토큰",
				content = @Content(
						mediaType = "application/json",
						schema = @Schema(implementation = ErrorResponse.class)
				)
		)
	})
	public ResponseEntity<Void> logout(Authentication authentication) {
		
		Claims claims = (Claims)authentication.getPrincipal();

		Long loginUserId = Long.parseLong(claims.getSubject());
		
		authService.logout(loginUserId);
		
		return ResponseEntity.status(HttpStatus.OK).body(null);
		
	}

	@PostMapping("/refresh")
	@Operation(
			summary = "AccessToken 재발급",
			description = "RefreshToken을 사용하여 AccessToken을 재발급"
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "토큰 재발급 성공",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = RefreshResponseDto.class)
					)
			),
			@ApiResponse(
					responseCode = "401",
					description = "유효하지 않은 토큰",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class)
					)
			)
	})
	public ResponseEntity<RefreshResponseDto> refresh(@CookieValue(value = "refreshToken", required = false) String refreshToken) {

		TokenPair tokenPair = authService.refresh(refreshToken);

        String newAccessToken = tokenPair.getAccessToken();
        String newRefreshToken = tokenPair.getRefreshToken();

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", newRefreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();

        RefreshResponseDto refreshResponseDto = RefreshResponseDto.of(newAccessToken);

		return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(refreshResponseDto);

	}
	
}
