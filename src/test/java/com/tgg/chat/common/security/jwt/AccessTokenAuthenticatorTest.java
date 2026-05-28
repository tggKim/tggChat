package com.tgg.chat.common.security.jwt;

import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessTokenAuthenticatorTest {
    @Mock
    JwtUtils jwtUtils;

    @Mock
    RedisTokenStore redisTokenStore;

    @InjectMocks
    AccessTokenAuthenticator accessTokenAuthenticator;

    @Test
    @DisplayName("토큰 검증 성공")
    void authenticate_token_success() {
        // given
        String bearerString = "Bearer token";

        Claims claims = mock(Claims.class);

        when(jwtUtils.parseClaims("token")).thenReturn(claims);

        when(claims.getSubject()).thenReturn("1");

        when(redisTokenStore.matchesAccessToken(1L, "token")).thenReturn(true);

        when(claims.get("email", String.class)).thenReturn("test@test.com");
        when(claims.get("username", String.class)).thenReturn("testUsername");

        // when
        AuthenticatedUser authenticatedUser = accessTokenAuthenticator.authenticateBearerToken(bearerString);

        // then
        assertThat(authenticatedUser.getUserId()).isEqualTo(1L);
        assertThat(authenticatedUser.getEmail()).isEqualTo("test@test.com");
        assertThat(authenticatedUser.getUsername()).isEqualTo("testUsername");

        verify(jwtUtils, times(1)).parseClaims("token");
        verify(claims, times(1)).getSubject();
        verify(redisTokenStore, times(1)).matchesAccessToken(1L, "token");
        verify(claims, times(1)).get("email", String.class);
        verify(claims, times(1)).get("username", String.class);
    }

    @Test
    @DisplayName("토큰 검증 실패 - Authorization 헤더 없음")
    void authenticate_token_fail_missing_auth_header() {
        // given
        String nullBearerString = null;

        // when & then
        assertThatThrownBy(() -> accessTokenAuthenticator.authenticateBearerToken(nullBearerString))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_MISSING_AUTH_HEADER);

        verify(jwtUtils, never()).parseClaims(anyString());
        verify(redisTokenStore, never()).matchesAccessToken(anyLong(), anyString());
    }

    @Test
    @DisplayName("토큰 검증 실패 - Authorization 헤더가 Bearer 형식이 아님")
    void authenticate_token_fail_invalid_auth_scheme() {
        // given
        String invalidAuthScheme = "Basic token";

        // when & then
        assertThatThrownBy(() -> accessTokenAuthenticator.authenticateBearerToken(invalidAuthScheme))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_INVALID_AUTH_SCHEME);

        verify(jwtUtils, never()).parseClaims(anyString());
        verify(redisTokenStore, never()).matchesAccessToken(anyLong(), anyString());
    }

    @Test
    @DisplayName("토큰 검증 실패 - Redis 저장 AccessToken과 불일치")
    void authenticate_token_fail_access_token_mismatch() {
        // given
        String mismatchToken = "Bearer accessToken";

        Claims claims = mock(Claims.class);

        when(jwtUtils.parseClaims("accessToken")).thenReturn(claims);

        when(claims.getSubject()).thenReturn("1");

        when(redisTokenStore.matchesAccessToken(1L, "accessToken")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> accessTokenAuthenticator.authenticateBearerToken(mismatchToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_TOKEN_MISMATCH);

        verify(jwtUtils, times(1)).parseClaims("accessToken");
        verify(claims, times(1)).getSubject();
        verify(redisTokenStore, times(1)).matchesAccessToken(1L, "accessToken");
        verify(claims, never()).get("email", String.class);
        verify(claims, never()).get("username", String.class);
    }
}