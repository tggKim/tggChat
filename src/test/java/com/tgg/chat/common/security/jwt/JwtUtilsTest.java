package com.tgg.chat.common.security.jwt;

import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtUtilsTest {
    private final String SECRET_KEY = "0123456789abcdefghijklmnopqrstuvwxyz";

    private final JwtUtils jwtUtils = new JwtUtils(SECRET_KEY);

    @Test
    @DisplayName("AccessToken 생성 및 파싱 성공")
    void create_access_token_and_parse_claims_success() {
        // given
        User user = User.of("test@test.com", "testPassword", "testUsername");
        ReflectionTestUtils.setField(user, "userId", 1L);

        // when
        String accessToken = jwtUtils.createAccessToken(user);
        Claims claims = jwtUtils.parseClaims(accessToken);

        // then
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("test@test.com");
        assertThat(claims.get("username", String.class)).isEqualTo("testUsername");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("RefreshToken 생성 및 파싱 성공")
    void create_refresh_token_and_parse_claims_success() {
        // given
        User user = User.of("test@test.com", "testPassword", "testUsername");
        ReflectionTestUtils.setField(user, "userId", 1L);

        // when
        String refreshToken = jwtUtils.createRefreshToken(user);
        Claims claims = jwtUtils.parseClaims(refreshToken);

        // then
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("test@test.com");
        assertThat(claims.get("username", String.class)).isEqualTo("testUsername");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("토큰 만료 시간 조회 성공")
    void get_token_ttl_millis_success() {
        // when & then
        assertThat(jwtUtils.getAccessTokenTtlMillis()).isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(jwtUtils.getRefreshTokenTtlMillis()).isEqualTo(Duration.ofDays(7).toMillis());
    }

    @Test
    @DisplayName("Claims 파싱 실패 - 형식이 잘못된 토큰")
    void parse_claims_fail_malformed_token() {
        // given
        String malformedToken = "invalid.token.value";

        // when & then
        assertThatThrownBy(() -> jwtUtils.parseClaims(malformedToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_INVALID_TOKEN);
    }

    @Test
    @DisplayName("Claims 파싱 실패 - 서명이 유효하지 않은 토큰")
    void parse_claims_fail_invalid_signature() {
        // given
        JwtUtils anotherJwtUtils = new JwtUtils("0123456789abcdefghijklmnopqrstuvwxyzfake");

        User user = User.of("test@test.com", "testPassword", "testUsername");
        ReflectionTestUtils.setField(user, "userId", 1L);

        String invalidToken = anotherJwtUtils.createAccessToken(user);

        // when & then
        assertThatThrownBy(() -> jwtUtils.parseClaims(invalidToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_INVALID_TOKEN);
    }

    @Test
    @DisplayName("Claims 파싱 실패 - 기간이 만료된 토큰")
    void parse_claims_fail_expired_token() {
        // given
        Date now = new Date();

        String expiredToken = Jwts.builder()
                .setIssuedAt(new Date(now.getTime() - Duration.ofMinutes(2).toMillis()))
                .setExpiration(new Date(now.getTime() - Duration.ofMinutes(1).toMillis()))
                .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtUtils.parseClaims(expiredToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("Claims 파싱 실패 - 지원되지 않는 JWT 형식")
    void parse_claims_fail_unsupported_token() {
        // given
        String unsupportedToken = Jwts.builder()
                .setSubject("1")
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtUtils.parseClaims(unsupportedToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_UNSUPPORTED_TOKEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Claims 파싱 실패 - 빈값 혹은 null 값")
    void parse_claims_fail_empty_or_null_token(String token) {
        // when & then
        assertThatThrownBy(() -> jwtUtils.parseClaims(token))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_EMPTY_TOKEN);
    }
}