package com.tgg.chat.domain.user.service;

import com.tgg.chat.domain.user.dto.request.UserUpdateRequestDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.dto.response.UserResponseDto;
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
	private final PasswordEncoder passwordEncoder;
	
	@Transactional
	public SignUpResponseDto signUpUser(SignUpRequestDto signUpRequestDto) {
		
		// 이메일 중복 검사
		if(userMapper.existsByEmail(signUpRequestDto.getEmail())) {
			throw new ErrorException(ErrorCode.DUPLICATE_EMAIL_ERROR);
		}
		
		String encodedPassword = passwordEncoder.encode(signUpRequestDto.getPassword());
		
		User requestUser = User.of(signUpRequestDto.getEmail(), encodedPassword, signUpRequestDto.getUsername());
		
		User savedUser = userRepository.save(requestUser);
		
		return SignUpResponseDto.of(savedUser);
		
	}
	
	@Transactional(readOnly = true)
	public UserResponseDto findUser(Long userId) {
		
		User findUser = userMapper.findById(userId);
		
		if(findUser == null || findUser.getDeleted()) {
			throw new ErrorException(ErrorCode.USER_NOT_FOUND);
		}
		
		return UserResponseDto.of(findUser);
		
	}

	@Transactional
	public void updateUser(UserUpdateRequestDto userUpdateRequestDto) {

		User findUser = userRepository.findById(userUpdateRequestDto.getUserId()).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));

		if(findUser.getDeleted()) {
			throw new ErrorException(ErrorCode.USER_NOT_FOUND);
		}

		findUser.update(userUpdateRequestDto.getUsername());

	}

	@Transactional
	public void deleteUser(Long userId) {

		User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));

		if(findUser.getDeleted()) {
			throw new ErrorException(ErrorCode.USER_NOT_FOUND);
		}

		findUser.deleteUser();

	}
	
}
