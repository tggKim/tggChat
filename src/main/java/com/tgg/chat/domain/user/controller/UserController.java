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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	
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
