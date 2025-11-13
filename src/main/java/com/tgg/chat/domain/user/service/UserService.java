package com.tgg.chat.domain.user.service;

import org.springframework.stereotype.Service;

import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	
	public SignUpResponseDto signUpUser(SignUpRequestDto signUpRequestDto) {
		
		// 이메일 중복 검사
		if(userMapper.existsByEmail(signUpRequestDto.getEmail())) {
			throw new ErrorException(ErrorCode.DUPLICATE_EMAIL_ERROR);
		}
		
		User requestUser = User.of(signUpRequestDto.getEmail(), signUpRequestDto.getPassword(), signUpRequestDto.getUsername());
		
		User savedUser = userRepository.save(requestUser);
		
		return SignUpResponseDto.of(savedUser);
		
	}
	
}
