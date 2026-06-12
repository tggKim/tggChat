package com.tgg.chat.domain.auth.service;

import com.tgg.chat.domain.auth.dto.response.TokenPair;
import io.jsonwebtoken.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	
	private final JwtUtils jwtUtils;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RedisTokenStore redisTokenStore;
	
	// 로그인
	public TokenPair login(LoginRequestDto loginRequestDto, String refreshToken) {

        User findUser = findActiveUserByEmail(loginRequestDto.getEmail());
		
		boolean passwordMatch = passwordEncoder.matches(loginRequestDto.getPassword(), findUser.getPassword());
		if(!passwordMatch) {
			throw new ErrorException(ErrorCode.INVALID_PASSWORD);
		}

        // 기존 세션에서의 refreshToken이 남아있다면 redis에서 제거
        if(refreshToken != null) {
            try {
                Claims claims = jwtUtils.parseClaims(refreshToken);

                if(jwtUtils.isRefreshToken(claims)) {
                    String sid = jwtUtils.getSid(claims);
                    Long userId = Long.parseLong(claims.getSubject());
                    redisTokenStore.deleteRefreshToken(userId, sid);
                }
            } catch(ErrorException e) {
                // 기존 쿠키에 있던 refreshToken 파싱 실패는 새 로그인을 막지 않는다.
            }
        }

        String sid = jwtUtils.generateSid();
		String newAccessToken = jwtUtils.createAccessToken(findUser, sid);
		String newRefreshToken = jwtUtils.createRefreshToken(findUser, sid);
		
		// RefreshToken 레디스에 저장
        Long userId = findUser.getUserId();
        storeRefreshToken(userId, sid, newRefreshToken);
		
		return TokenPair.of(newAccessToken, newRefreshToken);
		
	}
	
	// 로그아웃
	public void logout(Long userId, String sid) {
        redisTokenStore.deleteRefreshToken(userId, sid);
	}

	// 토큰 재발급
	public TokenPair refresh(String refreshToken) {
		Claims claims = jwtUtils.parseClaims(refreshToken);
        if(!jwtUtils.isRefreshToken(claims)) {
            throw new ErrorException(ErrorCode.JWT_INVALID_REFRESH_TOKEN);
        }

		String sid = jwtUtils.getSid(claims);
		if(!redisTokenStore.matchesRefreshToken(sid, refreshToken)) {
			throw new ErrorException(ErrorCode.JWT_INVALID_REFRESH_TOKEN);
		}

        Long userId = Long.parseLong(claims.getSubject());
		User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

		String newRefreshToken = jwtUtils.createRefreshToken(findUser, sid);
		String newAccessToken = jwtUtils.createAccessToken(findUser, sid);

        storeRefreshToken(userId, sid, newRefreshToken);

		return TokenPair.of(newAccessToken, newRefreshToken);

	}
	
	private void storeRefreshToken(Long userId, String sid, String refreshToken) {
		long refreshTokenTTL = jwtUtils.getRefreshTokenTtlMillis();
		redisTokenStore.saveRefreshToken(userId, sid, refreshToken, refreshTokenTTL);
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
