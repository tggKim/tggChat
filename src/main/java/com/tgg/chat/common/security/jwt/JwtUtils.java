package com.tgg.chat.common.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import io.jsonwebtoken.Claims;
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
	private final Key SECRET_KEY;

	public JwtUtils(@Value("${JWT_SECRET_KEY}") String jwtSecretKey) {
        SECRET_KEY = Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
	}
	
	// accessToken, refreshToken 유효기간 설정
    private static final long ACCESS_TOKEN_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final long REFRESH_TOKEN_MILLIS = Duration.ofDays(7).toMillis();

    public String createAccessToken(User user) {
        return createToken(user, ACCESS_TOKEN_MILLIS);
    }

    public String createRefreshToken(User user) {
        return createToken(user, REFRESH_TOKEN_MILLIS);
    }

    private String createToken(User user, long ttlMillis) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttlMillis);

        String userId = String.valueOf(user.getUserId());
        Map<String, Object> claims = buildClaims(user);

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    private Map<String, Object> buildClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("username", user.getUsername());
        return claims;
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
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
