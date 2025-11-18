package com.tgg.chat.domain.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tgg.chat.common.jwt.JwtUtils;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginResponseDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	
	private final JwtUtils jwtUtils;
	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	
	public LoginResponseDto login(LoginRequestDto loginRequestDto) {
		
		User findUser = userMapper.findByEmail(loginRequestDto.getEmail());

		if(findUser == null || findUser.getDeleted()) {
			throw new ErrorException(ErrorCode.USER_NOT_FOUND);
		}
		
		boolean passwordMatch = passwordEncoder.matches(loginRequestDto.getPassword(), findUser.getPassword());
		if(!passwordMatch) {
			throw new ErrorException(ErrorCode.INVALID_PASSWORD);
		}
		
		String accessToken = jwtUtils.createAccessToken(findUser);
		String refreshToken = jwtUtils.createRefreshToken(findUser);
		
		return LoginResponseDto.of(accessToken, refreshToken);
		
	}
	
}
