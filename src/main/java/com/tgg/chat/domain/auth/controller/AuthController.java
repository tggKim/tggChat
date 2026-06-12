package com.tgg.chat.domain.auth.controller;

import com.tgg.chat.domain.auth.dto.response.RefreshResponseDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import io.swagger.v3.oas.annotations.headers.Header;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginResponseDto;
import com.tgg.chat.domain.auth.service.AuthService;
import com.tgg.chat.exception.ErrorResponse;

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
	private final JwtUtils jwtUtils;
	
	@PostMapping("/login")
	@Operation(
			summary = "로그인", 
			description = "이메일과 비밀번호로 로그인 합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "로그인 성공",
                headers = @Header(
                        name = "Set-Cookie",
                        description = "새 RefreshToken 쿠키 발급. HttpOnly, SameSite=Lax, Path=/",
                        schema = @Schema(
                                type = "string",
                                example = "refreshToken=eyJhbGciOiJIUzI1NiJ9...; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax"
                        )
                ),
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
	public ResponseEntity<LoginResponseDto> login(@RequestBody @Valid LoginRequestDto loginRequestDto, @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        TokenPair tokenPair = authService.login(loginRequestDto, refreshToken);

        String newAccessToken = tokenPair.getAccessToken();
        String newRefreshToken = tokenPair.getRefreshToken();

        ResponseCookie rtCookie = buildRefreshTokenCookie(newRefreshToken);

        LoginResponseDto loginResponseDto = LoginResponseDto.of(newAccessToken);
		
		return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(loginResponseDto);
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
                headers = @Header(
                        name = "Set-Cookie",
                        description = "RefreshToken 쿠키 만료. HttpOnly, SameSite=Lax, Path=/",
                        schema = @Schema(
                                type = "string",
                                example = "refreshToken=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"
                        )
                )
		),
		@ApiResponse(
				responseCode = "401",
				description = "JWT 인증 실패",
				content = @Content(
						mediaType = "application/json",
						schema = @Schema(implementation = ErrorResponse.class)
				)
		)
	})
	public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {		
		authService.logout(authenticatedUser.getUserId(), authenticatedUser.getSid());

        ResponseCookie responseCookie = buildExpiredRefreshTokenCookie();

		return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(null);
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
                    headers = @Header(
                            name = "Set-Cookie",
                            description = "새 RefreshToken 쿠키 발급. HttpOnly, SameSite=Lax, Path=/",
                            schema = @Schema(
                                    type = "string",
                                    example = "refreshToken=eyJhbGciOiJIUzI1NiJ9...; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax"
                            )
                    ),
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = RefreshResponseDto.class)
					)
			),
			@ApiResponse(
					responseCode = "401",
					description = "RefreshToken 누락, 만료 또는 유효하지 않음",
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
	public ResponseEntity<RefreshResponseDto> refresh(@CookieValue(value = "refreshToken", required = false) String refreshToken) {
		TokenPair tokenPair = authService.refresh(refreshToken);

        String newAccessToken = tokenPair.getAccessToken();
        String newRefreshToken = tokenPair.getRefreshToken();

        ResponseCookie rtCookie = buildRefreshTokenCookie(newRefreshToken);

        RefreshResponseDto refreshResponseDto = RefreshResponseDto.of(newAccessToken);

		return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(refreshResponseDto);
	}

	private ResponseCookie buildRefreshTokenCookie(String refreshToken) {
		return ResponseCookie.from("refreshToken", refreshToken)
			.httpOnly(true) // 이 쿠키는 자바스크립트로 접근 불가
			.secure(false) // http 환경에서만 쿠키 전송
			.sameSite("Lax") // 다른 사이트에서 링크를 클릭시 쿠키가 보내지도록 허용하는 옵션
			.path("/") // 모든 경로의 요청에 쿠키 포함
			.maxAge(Duration.ofMillis(jwtUtils.getRefreshTokenTtlMillis())) // 만료시간 설정
			.build();
	}

    private ResponseCookie buildExpiredRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true) // 이 쿠키는 자바스크립트로 접근 불가
                .secure(false) // http 환경에서만 쿠키 전송
                .sameSite("Lax") // 다른 사이트에서 링크를 클릭시 쿠키가 보내지도록 허용하는 옵션
                .path("/") // 모든 경로의 요청에 쿠키 포함
                .maxAge(0) // 만료시간 설정
                .build();
    }
}
