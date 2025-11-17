package com.tgg.chat.common.jwt;

import java.security.Key;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties.Jwt;
import org.springframework.stereotype.Component;

import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import io.jsonwebtoken.ClaimsMutator;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@Component
public class JwtUtils {
	
	// 비밀키 생성
	private final Key SECREAT_KEY;

	public JwtUtils(@Value("${JWT_SECRET_KEY}") String jwtSecretKey) {
		SECREAT_KEY = Keys.hmacShaKeyFor(jwtSecretKey.getBytes());
	}
	
	// accessToken, refreshToekn 유효기간 설정
	private static final Long ACCESS_TOKEN_MILLIS = 30 * 60 * 1000L;
	private static final Long REFRESH_TOKEN_MILLS = 7 * 24 * 60 * 60 * 1000L;
	
	public String createAccessToken(User user) {
		
		Date now = new Date();
		Date expiration = new Date(now.getTime() + ACCESS_TOKEN_MILLIS);
		
		Map<String, Object> claims = new HashMap<>();
		claims.put("email", user.getEmail());
		claims.put("username", user.getUsername());
		
		String subject = String.valueOf(user.getUserId());
		
		return Jwts.builder()
			.setHeaderParam("typ", "JWT")
			.setSubject(subject)
			.setClaims(claims)
			.setIssuedAt(now)
			.setExpiration(expiration)
			.signWith(SECREAT_KEY, SignatureAlgorithm.HS256)
			.compact();
		
	}
	
	public String createRefreshToken(User user) {
		
		Date now = new Date();
		Date expiration = new Date(now.getTime() + REFRESH_TOKEN_MILLS);
		
		Map<String, Object> claims = new HashMap<>();
		claims.put("email", user.getEmail());
		claims.put("username", user.getUsername());
		
		String subject = String.valueOf(user.getUserId());
		
		return Jwts.builder()
			.setHeaderParam("typ", "JWT")
			.setSubject(subject)
			.setClaims(claims)
			.setIssuedAt(now)
			.setExpiration(expiration)
			.signWith(SECREAT_KEY, SignatureAlgorithm.HS256)
			.compact();
		
	}
	
	public void validateToken(String token) {
		try {
			
			Jwts.parserBuilder()
				.setSigningKey(SECREAT_KEY)
				.build()
				.parse(token);
		
		} catch (SignatureException | SecurityException | MalformedJwtException e) {
			
			// 유효하지 않은 JWT
			throw new ErrorException(ErrorCode.JWT_INVALID_TOKEN);
		
		} catch (ExpiredJwtException e) {
			
			// 만료된 JWT
			throw new ErrorException(ErrorCode.JWT_EXPIRED_TOKEN);
		
		} catch (UnsupportedJwtException e) {
			
			// 지원되지 않는 JWT 형식
			throw new ErrorException(ErrorCode.JWT_UNSUPPORTED_TOKEN);
		
		} catch (IllegalArgumentException e) {
			
			// 토큰이 빈값 혹은 null 값
			throw new ErrorException(ErrorCode.JWT_EMPTY_TOKEN);
		
		}
 	}
	
}
