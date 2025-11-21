package com.tgg.chat.domain.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tgg.chat.common.jwt.JwtUtils;
import com.tgg.chat.common.redis.RedisUtils;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginResponseDto;
import com.tgg.chat.domain.auth.dto.response.LoginStatusResponse;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	
	private final JwtUtils jwtUtils;
	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final RedisUtils redisUtils;
	
	// 로그인
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
		
		// AccessToken, RefreshToken 레디스에 저장
		storeTokenSet(findUser.getUserId(), accessToken, refreshToken);
		
		return LoginResponseDto.of(accessToken, refreshToken);
		
	}
	
	// 로그인 여부 확인
	public LoginStatusResponse isLogedIn(Long userId) {
		
		String refreshToken = redisUtils.getRefreshToken(userId);
		
		boolean isLoggedIn;
		
		if(refreshToken == null) {
			isLoggedIn = false;
		} else {
			isLoggedIn = true;
		}
		
		return LoginStatusResponse.of(isLoggedIn);
		
	}
	
	private void storeTokenSet(Long userId, String accessToken, String refreshToken) {

		Claims accessTokenClaims = jwtUtils.getClaims(accessToken);
		Claims refreshTokenClaims = jwtUtils.getClaims(refreshToken);
		
		Long accessTokenExpireTime = accessTokenClaims.getExpiration().getTime();
		Long refreshTokenExpireTime = refreshTokenClaims.getExpiration().getTime();
		
		Long currentTime = System.currentTimeMillis();
		
		Long accessTokenTTL = accessTokenExpireTime - currentTime;
		Long refreshTokenTTL = refreshTokenExpireTime - currentTime;
		
		redisUtils.saveAccessToken(userId , accessToken, accessTokenTTL);
		redisUtils.saveRefreshToken(userId, refreshToken, refreshTokenTTL);
		
	}
	
}
