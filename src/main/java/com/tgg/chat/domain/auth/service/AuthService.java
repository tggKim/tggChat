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
	private final RedisTokenStore redisUtils;
	
	// 로그인
	public TokenPair login(LoginRequestDto loginRequestDto) {

        User findUser = userRepository.findByEmail(loginRequestDto.getEmail())
                .orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));

		if(findUser.getDeleted()) {
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
		
		return TokenPair.of(accessToken, refreshToken);
		
	}
	
	// 로그인 여부 확인
	public LoginStatusResponseDto isLoggedIn(LoginStatusRequestDto loginStatusRequestDto) {

        User findUser = userRepository.findByEmail(loginStatusRequestDto.getEmail())
                .orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));

        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }
		
		String refreshToken = redisUtils.getRefreshToken(findUser.getUserId());
		
		boolean isLoggedIn;
		
		if(refreshToken == null) {
			isLoggedIn = false;
		} else {
			isLoggedIn = true;
		}
		
		return LoginStatusResponseDto.of(isLoggedIn);
		
	}
	
	// 로그아웃
	public void logout(Long userId) {
		
		redisUtils.deleteAccessToken(userId);
		redisUtils.deleteRefreshToken(userId);
		
	}

	// 토큰 재발급
	public TokenPair refresh(String refreshToken) {
		Claims claims = jwtUtils.parseClaims(refreshToken);

		Long userId = Long.parseLong(claims.getSubject());

		String findRefreshToken = redisUtils.getRefreshToken(userId);
		// RefreshToken 이 존재하지 않거나 유효하지 않으면 예외처리
		if(findRefreshToken == null || !findRefreshToken.equals(refreshToken)) {
			throw new ErrorException(ErrorCode.JWT_INVALID_REFRESH_TOKEN);
		}

		User findUser = userMapper.findById(userId);

		String newRefreshToken = jwtUtils.createRefreshToken(findUser);
		String newAccessToken = jwtUtils.createAccessToken(findUser);

		storeTokenSet(userId, newAccessToken, newRefreshToken);

		return TokenPair.of(newAccessToken, newRefreshToken);

	}
	
	private void storeTokenSet(Long userId, String accessToken, String refreshToken) {

		Claims accessTokenClaims = jwtUtils.parseClaims(accessToken);
		Claims refreshTokenClaims = jwtUtils.parseClaims(refreshToken);
		
		long accessTokenExpireTime = accessTokenClaims.getExpiration().getTime();
		long refreshTokenExpireTime = refreshTokenClaims.getExpiration().getTime();
		
		long currentTime = System.currentTimeMillis();
		
		long accessTokenTTL = accessTokenExpireTime - currentTime;
		long refreshTokenTTL = refreshTokenExpireTime - currentTime;
		
		redisUtils.saveAccessToken(userId , accessToken, accessTokenTTL);
		redisUtils.saveRefreshToken(userId, refreshToken, refreshTokenTTL);
		
	}
	
}
