package com.tgg.chat.common.security.jwt;

import com.tgg.chat.common.security.principal.AuthenticatedUser;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessTokenAuthenticatorTest {
    @Mock
    JwtUtils jwtUtils;

    @InjectMocks
    AccessTokenAuthenticator accessTokenAuthenticator;

    @Test
    @DisplayName("토큰 검증 성공")
    void authenticate_token_success() {
        // given
        String bearerString = "Bearer token";

        Claims claims = mock(Claims.class);

        when(jwtUtils.parseClaims("token")).thenReturn(claims);

        when(jwtUtils.isAccessToken(claims)).thenReturn(true);

        when(claims.getSubject()).thenReturn("1");
        when(jwtUtils.getSid(claims)).thenReturn("sid");

        // when
        AuthenticatedUser authenticatedUser = accessTokenAuthenticator.authenticateBearerToken(bearerString);

        // then
        assertThat(authenticatedUser.getUserId()).isEqualTo(1L);
        assertThat(authenticatedUser.getSid()).isEqualTo("sid");

        verify(jwtUtils, times(1)).parseClaims("token");
        verify(jwtUtils, times(1)).isAccessToken(claims);
        verify(claims, times(1)).getSubject();
        verify(jwtUtils, times(1)).getSid(claims);
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
        verify(jwtUtils, never()).isAccessToken(any(Claims.class));
        verify(jwtUtils, never()).getSid(any(Claims.class));
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
        verify(jwtUtils, never()).isAccessToken(any(Claims.class));
        verify(jwtUtils, never()).getSid(any(Claims.class));
    }

    @Test
    @DisplayName("토큰 검증 실패 - 토큰이 accessToken 이 아님")
    void authenticate_token_fail_invalid_access_token() {
        // given
        String bearerString = "Bearer token";

        Claims claims = mock(Claims.class);

        when(jwtUtils.parseClaims("token")).thenReturn(claims);

        when(jwtUtils.isAccessToken(claims)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> accessTokenAuthenticator.authenticateBearerToken(bearerString))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_INVALID_TOKEN);

        verify(jwtUtils, times(1)).parseClaims("token");
        verify(jwtUtils, times(1)).isAccessToken(claims);
        verify(claims, never()).getSubject();
        verify(jwtUtils, never()).getSid(any(Claims.class));
    }
}