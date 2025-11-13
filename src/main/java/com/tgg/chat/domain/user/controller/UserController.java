package com.tgg.chat.domain.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
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
	
}
