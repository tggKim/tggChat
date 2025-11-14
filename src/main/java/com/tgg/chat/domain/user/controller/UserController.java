package com.tgg.chat.domain.user.controller;

import com.tgg.chat.domain.user.dto.request.UserUpdateRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "User API", description = "유저 CRUD API")
@RestController
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	
	@Operation(
		summary = "회원 가입",
		description =  "사용자를 신규 등록합니다."
	)
	@ApiResponses({
		@ApiResponse(
				responseCode = "200", 
				description = "조회 성공",
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
	@PostMapping("/user")
	public ResponseEntity<SignUpResponseDto> signUpUser(@RequestBody @Valid SignUpRequestDto signUpRequestDto) {
		
		SignUpResponseDto signUpResponseDto = userService.signUpUser(signUpRequestDto);
		
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(signUpResponseDto);
		
	}
	
	@GetMapping("/user/{userId}")
	public ResponseEntity<UserResponseDto> findUser(@PathVariable Long userId) {
		
		UserResponseDto userResponseDto = userService.findUser(userId);
		
		return ResponseEntity
				.status(HttpStatus.OK)
				.body(userResponseDto);
		
	}

	@PatchMapping("/user/{userId}")
	public ResponseEntity<Void> updateUser(@PathVariable Long userId, @RequestBody @Valid UserUpdateRequestDto userUpdateRequestDto) {

		userService.updateUser(userId, userUpdateRequestDto);

		return ResponseEntity
				.status(HttpStatus.OK)
				.body(null);

	}

	@DeleteMapping("/user/{userId}")
	public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {

		userService.deleteUser(userId);

		return ResponseEntity
				.status(HttpStatus.OK)
				.body(null);

	}
	
}
