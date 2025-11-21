package com.tgg.chat.domain.user.controller;

import com.tgg.chat.domain.user.dto.request.UserUpdateRequestDto;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.dto.response.UserResponseDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.service.UserService;
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

@Tag(name = "User API", description = "유저 CRUD API")
@RestController
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	
	@PostMapping("/user")
	@Operation(
		summary = "회원 가입",
		description =  "사용자를 신규 등록합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "등록 성공",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = SignUpResponseDto.class)
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
				responseCode = "403", 
				description = "중복된 이메일",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = ErrorResponse.class)
				)
		)
	})
	public ResponseEntity<SignUpResponseDto> signUpUser(@RequestBody @Valid SignUpRequestDto signUpRequestDto) {
		
		SignUpResponseDto signUpResponseDto = userService.signUpUser(signUpRequestDto);
		
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(signUpResponseDto);
		
	}
	
	@GetMapping("/user/{userId}")
	@Operation(
			summary = "회원 조회",
			description =  "userId로 회원을 조회 합니다."
		)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "조회 성공",
				content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = UserResponseDto.class)
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
	public ResponseEntity<UserResponseDto> findUser(@PathVariable Long userId) {
		
		UserResponseDto userResponseDto = userService.findUser(userId);
		
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(userResponseDto);
		
	}

	@PatchMapping("/user/{userId}")
	@SecurityRequirement(name = "JWT Auth")
	@Operation(
			summary = "회원 수정",
			description =  "회원 이름을 수정합니다."
		)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "수정 성공",
				content = @Content(
					mediaType = "application/json"
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
	public ResponseEntity<Void> updateUser(Authentication authentication, @PathVariable Long userId, @RequestBody @Valid UserUpdateRequestDto userUpdateRequestDto) {

		Claims claims = (Claims)authentication.getPrincipal();

		Long loginUserId = Long.parseLong(claims.getSubject());

		userService.updateUser(loginUserId, userId, userUpdateRequestDto);

		return ResponseEntity
				.status(HttpStatus.OK)
				.body(null);

	}

	@DeleteMapping("/user/{userId}")
	@SecurityRequirement(name = "JWT Auth")
	@Operation(
			summary = "회원 삭제",
			description =  "회원을 삭제합니다."
		)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "삭제 성공",
				content = @Content(
					mediaType = "application/json"
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
	public ResponseEntity<Void> deleteUser(Authentication authentication, @PathVariable Long userId) {

		Claims claims = (Claims)authentication.getPrincipal();

		Long loginUserId = Long.parseLong(claims.getSubject());
		
		userService.deleteUser(loginUserId, userId);

		return ResponseEntity
				.status(HttpStatus.OK)
				.body(null);

	}
	
}
