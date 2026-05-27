package com.tgg.chat.common.security.jwt;

import com.tgg.chat.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
}