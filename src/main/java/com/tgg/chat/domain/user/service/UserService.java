package com.tgg.chat.domain.user.service;

import org.springframework.stereotype.Service;

import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	
	public SignUpResponseDto saveUser(SignUpRequestDto signUpRequestDto) {
		
		User requestUser = User.of(signUpRequestDto.getEmail(), signUpRequestDto.getPassword(), signUpRequestDto.getUsername());
		
		User savedUser = userRepository.save(requestUser);
		
		return SignUpResponseDto.of(savedUser);
		
	}
	
}
