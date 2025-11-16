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

import io.jsonwebtoken.ClaimsMutator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {

	@Value("${JWT_SECRET_KEY}")
	private String jwtSecretKey;
	
	// 비밀키 생성
	private Key SECREAT_KEY;

	@PostConstruct
	public void init() {
		this.SECREAT_KEY = Keys.hmacShaKeyFor(jwtSecretKey.getBytes());
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
			.setClaims(claims)
			.setSubject(subject)
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
	
}
