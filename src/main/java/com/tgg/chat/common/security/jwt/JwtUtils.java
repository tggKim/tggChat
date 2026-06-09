package com.tgg.chat.common.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    private static final String CLAIM_SID = "sid";
    private static final String CLAIM_TYPE = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private static final long ACCESS_TOKEN_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long REFRESH_TOKEN_MILLIS = Duration.ofDays(7).toMillis();

    private final Key secretKey;

    public JwtUtils(@Value("${JWT_SECRET_KEY}") String jwtSecretKey) {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(User user, String sid) {
        return createToken(user, sid, ACCESS_TOKEN_TYPE, ACCESS_TOKEN_MILLIS);
    }

    public String createRefreshToken(User user, String sid) {
        return createToken(user, sid, REFRESH_TOKEN_TYPE, REFRESH_TOKEN_MILLIS);
    }

    private String createToken(User user, String sid, String tokenType, long ttlMillis) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttlMillis);

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setClaims(buildClaims(sid, tokenType))
                .setSubject(String.valueOf(user.getUserId()))
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private Map<String, Object> buildClaims(String sid, String tokenType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_SID, sid);
        claims.put(CLAIM_TYPE, tokenType);
        return claims;
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (SignatureException | MalformedJwtException e) {
            throw new ErrorException(ErrorCode.JWT_INVALID_TOKEN);
        } catch (ExpiredJwtException e) {
            throw new ErrorException(ErrorCode.JWT_EXPIRED_TOKEN);
        } catch (UnsupportedJwtException e) {
            throw new ErrorException(ErrorCode.JWT_UNSUPPORTED_TOKEN);
        } catch (IllegalArgumentException e) {
            throw new ErrorException(ErrorCode.JWT_EMPTY_TOKEN);
        }
    }

    public boolean isAccessToken(Claims claims) {
        return ACCESS_TOKEN_TYPE.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return REFRESH_TOKEN_TYPE.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public String getSid(Claims claims) {
        return claims.get(CLAIM_SID, String.class);
    }

    public long getAccessTokenTtlMillis() {
        return ACCESS_TOKEN_MILLIS;
    }

    public long getRefreshTokenTtlMillis() {
        return REFRESH_TOKEN_MILLIS;
    }

    public String generateSid() {
        return UUID.randomUUID().toString();
    }
}
