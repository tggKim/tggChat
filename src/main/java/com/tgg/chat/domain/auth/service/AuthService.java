package com.tgg.chat.domain.auth.service;

import com.tgg.chat.domain.auth.dto.request.RefreshRequestDto;
import com.tgg.chat.domain.auth.dto.response.RefreshResponseDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tgg.chat.common.jwt.JwtUtils;
import com.tgg.chat.common.redis.RedisUtils;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.request.LoginStatusRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginResponseDto;
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
	private final RedisUtils redisUtils;
	
	// 로그인
	public TokenPair login(LoginRequestDto loginRequestDto) {
		
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
		
		return TokenPair.of(accessToken, refreshToken);
		
	}
	
	// 로그인 여부 확인
	public LoginStatusResponseDto isLoggedIn(LoginStatusRequestDto loginStatusRequestDto) {
		
		User findUser = userMapper.findByEmail(loginStatusRequestDto.getEmail());
		
		if(findUser == null || findUser.getDeleted()) {
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
	public RefreshResponseDto refresh(RefreshRequestDto refreshRequestDto) {

		String refreshToken = refreshRequestDto.getRefreshToken();

		jwtUtils.validateToken(refreshToken);

		Claims claims = jwtUtils.getClaims(refreshToken);
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

		return RefreshResponseDto.of(newAccessToken, newRefreshToken);

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
