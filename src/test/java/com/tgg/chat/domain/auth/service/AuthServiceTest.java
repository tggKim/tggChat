package com.tgg.chat.domain.auth.service;

import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    JwtUtils jwtUtils;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    RedisTokenStore redisTokenStore;

    @InjectMocks
    AuthService authService;

    @Test
    @DisplayName("로그인 성공 - 기존 refreshToken 쿠키가 있으면 기존 세션 정리 후 새 토큰 발급")
    void login_success_with_existing_refresh_token_cookie() {
        // given
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");

        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(findUser));

        when(passwordEncoder.matches("testPassword", "encoded-password")).thenReturn(true);

        Claims claims = mock(Claims.class);
        when(jwtUtils.parseClaims("cookie-refreshToken")).thenReturn(claims);
        when(jwtUtils.isRefreshToken(claims)).thenReturn(true);
        when(jwtUtils.getSid(claims)).thenReturn("cookie-sid");

        when(jwtUtils.generateSid()).thenReturn("newSid");
        when(jwtUtils.createAccessToken(findUser, "newSid")).thenReturn("accessToken");
        when(jwtUtils.createRefreshToken(findUser, "newSid")).thenReturn("refreshToken");

        when(jwtUtils.getRefreshTokenTtlMillis()).thenReturn(2000L);

        // when
        TokenPair tokenPair = authService.login(requestDto, "cookie-refreshToken");

        // then
        assertThat(tokenPair.getAccessToken()).isEqualTo("accessToken");
        assertThat(tokenPair.getRefreshToken()).isEqualTo("refreshToken");

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches("testPassword", "encoded-password");

        verify(jwtUtils, times(1)).parseClaims("cookie-refreshToken");
        verify(jwtUtils, times(1)).isRefreshToken(claims);
        verify(jwtUtils, times(1)).getSid(claims);
        verify(redisTokenStore, times(1)).deleteRefreshToken("cookie-sid");

        verify(jwtUtils, times(1)).generateSid();
        verify(jwtUtils, times(1)).createAccessToken(findUser, "newSid");
        verify(jwtUtils, times(1)).createRefreshToken(findUser, "newSid");

        verify(jwtUtils, times(1)).getRefreshTokenTtlMillis();
        verify(redisTokenStore, times(1)).saveRefreshToken("newSid", "refreshToken", 2000L);
    }

    @Test
    @DisplayName("로그인 성공 - 기존 refreshToken 쿠키가 없어도 새 토큰 발급")
    void login_success_without_refresh_token_cookie() {
        // given
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");

        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(findUser));

        when(passwordEncoder.matches("testPassword", "encoded-password")).thenReturn(true);

        when(jwtUtils.generateSid()).thenReturn("newSid");
        when(jwtUtils.createAccessToken(findUser, "newSid")).thenReturn("accessToken");
        when(jwtUtils.createRefreshToken(findUser, "newSid")).thenReturn("refreshToken");

        when(jwtUtils.getRefreshTokenTtlMillis()).thenReturn(2000L);

        // when
        TokenPair tokenPair = authService.login(requestDto, null);

        // then
        assertThat(tokenPair.getAccessToken()).isEqualTo("accessToken");
        assertThat(tokenPair.getRefreshToken()).isEqualTo("refreshToken");

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches("testPassword", "encoded-password");

        verify(jwtUtils, never()).parseClaims(anyString());
        verify(jwtUtils, never()).isRefreshToken(any(Claims.class));
        verify(jwtUtils, never()).getSid(any(Claims.class));
        verify(redisTokenStore, never()).deleteRefreshToken(anyString());

        verify(jwtUtils, times(1)).generateSid();
        verify(jwtUtils, times(1)).createAccessToken(findUser, "newSid");
        verify(jwtUtils, times(1)).createRefreshToken(findUser, "newSid");

        verify(jwtUtils, times(1)).getRefreshTokenTtlMillis();
        verify(redisTokenStore, times(1)).saveRefreshToken("newSid", "refreshToken", 2000L);
    }

    @Test
    @DisplayName("로그인 성공 - 기존 refreshToken 쿠키 파싱 실패 시 세션 정리 없이 새 토큰 발급")
    void login_success_when_refresh_token_cookie_parse_fails() {
        // given
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");

        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(findUser));

        when(passwordEncoder.matches("testPassword", "encoded-password")).thenReturn(true);

        when(jwtUtils.parseClaims("cookie-invalidRefreshToken")).thenThrow(new ErrorException(ErrorCode.JWT_INVALID_TOKEN));

        when(jwtUtils.generateSid()).thenReturn("newSid");
        when(jwtUtils.createAccessToken(findUser, "newSid")).thenReturn("accessToken");
        when(jwtUtils.createRefreshToken(findUser, "newSid")).thenReturn("refreshToken");

        when(jwtUtils.getRefreshTokenTtlMillis()).thenReturn(2000L);

        // when
        TokenPair tokenPair = authService.login(requestDto, "cookie-invalidRefreshToken");

        // then
        assertThat(tokenPair.getAccessToken()).isEqualTo("accessToken");
        assertThat(tokenPair.getRefreshToken()).isEqualTo("refreshToken");

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches("testPassword", "encoded-password");

        verify(jwtUtils, times(1)).parseClaims("cookie-invalidRefreshToken");
        verify(jwtUtils, never()).isRefreshToken(any(Claims.class));
        verify(jwtUtils, never()).getSid(any(Claims.class));
        verify(redisTokenStore, never()).deleteRefreshToken(anyString());

        verify(jwtUtils, times(1)).generateSid();
        verify(jwtUtils, times(1)).createAccessToken(findUser, "newSid");
        verify(jwtUtils, times(1)).createRefreshToken(findUser, "newSid");

        verify(jwtUtils, times(1)).getRefreshTokenTtlMillis();
        verify(redisTokenStore, times(1)).saveRefreshToken("newSid", "refreshToken", 2000L);
    }

    @Test
    @DisplayName("로그인 성공 - 기존 쿠키가 refreshToken이 아니면 세션 정리 없이 새 토큰 발급")
    void login_success_when_existing_cookie_is_not_refresh_token() {
        // given
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");

        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(findUser));

        when(passwordEncoder.matches("testPassword", "encoded-password")).thenReturn(true);

        Claims claims = mock(Claims.class);
        when(jwtUtils.parseClaims("cookie-notRefreshToken")).thenReturn(claims);
        when(jwtUtils.isRefreshToken(claims)).thenReturn(false);

        when(jwtUtils.generateSid()).thenReturn("newSid");
        when(jwtUtils.createAccessToken(findUser, "newSid")).thenReturn("accessToken");
        when(jwtUtils.createRefreshToken(findUser, "newSid")).thenReturn("refreshToken");

        when(jwtUtils.getRefreshTokenTtlMillis()).thenReturn(2000L);

        // when
        TokenPair tokenPair = authService.login(requestDto, "cookie-notRefreshToken");

        // then
        assertThat(tokenPair.getAccessToken()).isEqualTo("accessToken");
        assertThat(tokenPair.getRefreshToken()).isEqualTo("refreshToken");

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches("testPassword", "encoded-password");

        verify(jwtUtils, times(1)).parseClaims("cookie-notRefreshToken");
        verify(jwtUtils, times(1)).isRefreshToken(claims);
        verify(jwtUtils, never()).getSid(any(Claims.class));
        verify(redisTokenStore, never()).deleteRefreshToken(anyString());

        verify(jwtUtils, times(1)).generateSid();
        verify(jwtUtils, times(1)).createAccessToken(findUser, "newSid");
        verify(jwtUtils, times(1)).createRefreshToken(findUser, "newSid");

        verify(jwtUtils, times(1)).getRefreshTokenTtlMillis();
        verify(redisTokenStore, times(1)).saveRefreshToken("newSid", "refreshToken", 2000L);
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 유저")
    void login_fail_user_not_found() {
        // given
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");

        when(userRepository.findByEmail(requestDto.getEmail())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(requestDto, null))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());

        verify(jwtUtils, never()).parseClaims(anyString());
        verify(jwtUtils, never()).isRefreshToken(any(Claims.class));
        verify(jwtUtils, never()).getSid(any(Claims.class));
        verify(redisTokenStore, never()).deleteRefreshToken(anyString());

        verify(jwtUtils, never()).generateSid();
        verify(jwtUtils, never()).createAccessToken(any(User.class), anyString());
        verify(jwtUtils, never()).createRefreshToken(any(User.class), anyString());

        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
        verify(redisTokenStore, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("로그인 실패 - 삭제된 유저")
    void login_fail_deleted_user() {
        // given
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");

        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "deleted", true);
        when(userRepository.findByEmail(requestDto.getEmail())).thenReturn(Optional.of(findUser));

        // when & then
        assertThatThrownBy(() -> authService.login(requestDto, null))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());

        verify(jwtUtils, never()).parseClaims(anyString());
        verify(jwtUtils, never()).isRefreshToken(any(Claims.class));
        verify(jwtUtils, never()).getSid(any(Claims.class));
        verify(redisTokenStore, never()).deleteRefreshToken(anyString());

        verify(jwtUtils, never()).generateSid();
        verify(jwtUtils, never()).createAccessToken(any(User.class), anyString());
        verify(jwtUtils, never()).createRefreshToken(any(User.class), anyString());

        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
        verify(redisTokenStore, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 비밀번호")
    void login_fail_invalid_password() {
        // given
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");

        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        when(userRepository.findByEmail(requestDto.getEmail())).thenReturn(Optional.of(findUser));

        when(passwordEncoder.matches(requestDto.getPassword(), findUser.getPassword())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(requestDto, null))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches("testPassword", "encoded-password");

        verify(jwtUtils, never()).parseClaims(anyString());
        verify(jwtUtils, never()).isRefreshToken(any(Claims.class));
        verify(jwtUtils, never()).getSid(any(Claims.class));
        verify(redisTokenStore, never()).deleteRefreshToken(anyString());

        verify(jwtUtils, never()).generateSid();
        verify(jwtUtils, never()).createAccessToken(any(User.class), anyString());
        verify(jwtUtils, never()).createRefreshToken(any(User.class), anyString());

        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
        verify(redisTokenStore, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_success() {
        // given
        String sid = "sid";

        // when
        authService.logout(sid);

        // then
        verify(redisTokenStore, times(1)).deleteRefreshToken(sid);
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void token_refresh_success() {
        // given
        String refreshToken = "refreshToken";

        Claims claims = mock(Claims.class);
        when(jwtUtils.parseClaims(refreshToken)).thenReturn(claims);
        when(jwtUtils.isRefreshToken(claims)).thenReturn(true);

        when(jwtUtils.getSid(claims)).thenReturn("sid");
        when(redisTokenStore.matchesRefreshToken("sid", refreshToken)).thenReturn(true);

        when(claims.getSubject()).thenReturn("1");
        User user = User.of("test@test.com", "testPassword", "testUsername");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(jwtUtils.createRefreshToken(user, "sid")).thenReturn("newRefreshToken");
        when(jwtUtils.createAccessToken(user, "sid")).thenReturn("newAccessToken");
        when(jwtUtils.getRefreshTokenTtlMillis()).thenReturn(2000L);

        // when
        TokenPair tokenPair = authService.refresh(refreshToken);

        // then
        assertThat(tokenPair.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(tokenPair.getRefreshToken()).isEqualTo("newRefreshToken");

        verify(jwtUtils, times(1)).parseClaims(refreshToken);
        verify(jwtUtils, times(1)).isRefreshToken(claims);

        verify(jwtUtils, times(1)).getSid(claims);
        verify(redisTokenStore, times(1)).matchesRefreshToken("sid", refreshToken);

        verify(claims, times(1)).getSubject();
        verify(userRepository, times(1)).findById(1L);

        verify(jwtUtils, times(1)).createRefreshToken(user, "sid");
        verify(jwtUtils, times(1)).createAccessToken(user, "sid");
        verify(jwtUtils, times(1)).getRefreshTokenTtlMillis();

        verify(redisTokenStore, times(1)).saveRefreshToken("sid", "newRefreshToken", 2000L);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 리프레시 토큰이 아님")
    void token_refresh_fail_not_refresh_token() {
        // given
        String refreshToken = "refreshToken";

        Claims claims = mock(Claims.class);
        when(jwtUtils.parseClaims(refreshToken)).thenReturn(claims);
        when(jwtUtils.isRefreshToken(claims)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_INVALID_REFRESH_TOKEN);

        verify(jwtUtils, times(1)).parseClaims(refreshToken);
        verify(jwtUtils, times(1)).isRefreshToken(claims);

        verify(jwtUtils, never()).getSid(any(Claims.class));
        verify(redisTokenStore, never()).matchesRefreshToken(anyString(), anyString());

        verify(claims, never()).getSubject();
        verify(userRepository, never()).findById(anyLong());

        verify(jwtUtils, never()).createRefreshToken(any(User.class), anyString());
        verify(jwtUtils, never()).createAccessToken(any(User.class), anyString());
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();

        verify(redisTokenStore, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("토큰 재발급 실패 - Redis에 저장된 refreshToken과 일치하지 않음")
    void token_refresh_fail_refresh_token_mismatch() {
        // given
        String refreshToken = "refreshToken";

        Claims claims = mock(Claims.class);
        when(jwtUtils.parseClaims(refreshToken)).thenReturn(claims);
        when(jwtUtils.isRefreshToken(claims)).thenReturn(true);

        when(jwtUtils.getSid(claims)).thenReturn("sid");
        when(redisTokenStore.matchesRefreshToken("sid", refreshToken)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.JWT_INVALID_REFRESH_TOKEN);

        verify(jwtUtils, times(1)).parseClaims(refreshToken);
        verify(jwtUtils, times(1)).isRefreshToken(claims);

        verify(jwtUtils, times(1)).getSid(claims);
        verify(redisTokenStore, times(1)).matchesRefreshToken("sid", refreshToken);

        verify(claims, never()).getSubject();
        verify(userRepository, never()).findById(anyLong());

        verify(jwtUtils, never()).createRefreshToken(any(User.class), anyString());
        verify(jwtUtils, never()).createAccessToken(any(User.class), anyString());
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();

        verify(redisTokenStore, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 존재하지 않는 유저")
    void token_refresh_fail_user_not_found() {
        // given
        String refreshToken = "refreshToken";

        Claims claims = mock(Claims.class);
        when(jwtUtils.parseClaims(refreshToken)).thenReturn(claims);
        when(jwtUtils.isRefreshToken(claims)).thenReturn(true);

        when(jwtUtils.getSid(claims)).thenReturn("sid");
        when(redisTokenStore.matchesRefreshToken("sid", refreshToken)).thenReturn(true);

        when(claims.getSubject()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(jwtUtils, times(1)).parseClaims(refreshToken);
        verify(jwtUtils, times(1)).isRefreshToken(claims);

        verify(jwtUtils, times(1)).getSid(claims);
        verify(redisTokenStore, times(1)).matchesRefreshToken("sid", refreshToken);

        verify(claims, times(1)).getSubject();
        verify(userRepository, times(1)).findById(1L);

        verify(jwtUtils, never()).createRefreshToken(any(User.class), anyString());
        verify(jwtUtils, never()).createAccessToken(any(User.class), anyString());
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();

        verify(redisTokenStore, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 삭제된 유저")
    void token_refresh_fail_deleted_user() {
        // given
        String refreshToken = "refreshToken";

        Claims claims = mock(Claims.class);
        when(jwtUtils.parseClaims(refreshToken)).thenReturn(claims);
        when(jwtUtils.isRefreshToken(claims)).thenReturn(true);

        when(jwtUtils.getSid(claims)).thenReturn("sid");
        when(redisTokenStore.matchesRefreshToken("sid", refreshToken)).thenReturn(true);

        when(claims.getSubject()).thenReturn("1");
        User findUser = User.of("test@test.com", "testPassword", "testUsername");
        ReflectionTestUtils.setField(findUser, "deleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when & then
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(jwtUtils, times(1)).parseClaims(refreshToken);
        verify(jwtUtils, times(1)).isRefreshToken(claims);

        verify(jwtUtils, times(1)).getSid(claims);
        verify(redisTokenStore, times(1)).matchesRefreshToken("sid", refreshToken);

        verify(claims, times(1)).getSubject();
        verify(userRepository, times(1)).findById(1L);

        verify(jwtUtils, never()).createRefreshToken(any(User.class), anyString());
        verify(jwtUtils, never()).createAccessToken(any(User.class), anyString());
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();

        verify(redisTokenStore, never()).saveRefreshToken(anyString(), anyString(), anyLong());
    }
}