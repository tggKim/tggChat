package com.tgg.chat.domain.auth.service;

import com.tgg.chat.domain.auth.dto.response.TokenPair;
import io.jsonwebtoken.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.request.LoginStatusRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginStatusResponseDto;
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
	private final RedisTokenStore redisTokenStore;
	
	// 로그인
	public TokenPair login(LoginRequestDto loginRequestDto) {

        User findUser = findActiveUserByEmail(loginRequestDto.getEmail());
		
		boolean passwordMatch = passwordEncoder.matches(loginRequestDto.getPassword(), findUser.getPassword());
		if(!passwordMatch) {
			throw new ErrorException(ErrorCode.INVALID_PASSWORD);
		}
		
		String accessToken = jwtUtils.createAccessToken(findUser);
		String refreshToken = jwtUtils.createRefreshToken(findUser);
		
		// AccessToken, RefreshToken 레디스에 저장
		storeTokenSet(findUser.getUserId(), accessToken, refreshToken);
		
		return TokenPair.of(accessToken, refreshToken);
		
	}
	
	// 로그인 여부 확인
	public LoginStatusResponseDto isLoggedIn(LoginStatusRequestDto loginStatusRequestDto) {
        User findUser = findActiveUserByEmail(loginStatusRequestDto.getEmail());
		
		boolean isLoggedIn = redisTokenStore.hasRefreshToken(findUser.getUserId());
		
		return LoginStatusResponseDto.of(isLoggedIn);
	}
	
	// 로그아웃
	public void logout(Long userId) {
		redisTokenStore.deleteUserTokenSets(userId);
	}

	// 토큰 재발급
	public TokenPair refresh(String refreshToken) {
		Claims claims = jwtUtils.parseClaims(refreshToken);

		Long userId = Long.parseLong(claims.getSubject());
		if(!redisTokenStore.matchesRefreshToken(userId, refreshToken)) {
			throw new ErrorException(ErrorCode.JWT_INVALID_REFRESH_TOKEN);
		}

		User findUser = userMapper.findById(userId);

		String newRefreshToken = jwtUtils.createRefreshToken(findUser);
		String newAccessToken = jwtUtils.createAccessToken(findUser);

		storeTokenSet(userId, newAccessToken, newRefreshToken);

		return TokenPair.of(newAccessToken, newRefreshToken);

	}
	
	private void storeTokenSet(Long userId, String accessToken, String refreshToken) {
		long accessTokenTTL = jwtUtils.getAccessTokenTtlMillis();
		long refreshTokenTTL = jwtUtils.getRefreshTokenTtlMillis();
		
		redisTokenStore.saveAccessToken(userId , accessToken, accessTokenTTL);
		redisTokenStore.saveRefreshToken(userId, refreshToken, refreshTokenTTL);
	}
	
	private User findActiveUserByEmail(String email) {
		User findUser = userRepository.findByEmail(email)
	              .orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
	
		if (findUser.getDeleted()) {
	    	throw new ErrorException(ErrorCode.USER_NOT_FOUND);
	    }
	
		return findUser;
	}
}
