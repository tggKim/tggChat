package com.tgg.chat.domain.auth.service;

import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.request.LoginStatusRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginStatusResponseDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
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
    @DisplayName("로그인 성공")
    void login_success() {
        // given
        LoginRequestDto requestDto = new LoginRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");

        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(findUser));

        when(passwordEncoder.matches("testPassword", "encoded-password")).thenReturn(true);

        when(jwtUtils.createAccessToken(findUser)).thenReturn("accessToken");
        when(jwtUtils.createRefreshToken(findUser)).thenReturn("refreshToken");

        when(jwtUtils.getAccessTokenTtlMillis()).thenReturn(1000L);
        when(jwtUtils.getRefreshTokenTtlMillis()).thenReturn(2000L);

        // when
        TokenPair tokenPair = authService.login(requestDto);

        // then
        assertThat(tokenPair.getAccessToken()).isEqualTo("accessToken");
        assertThat(tokenPair.getRefreshToken()).isEqualTo("refreshToken");

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches("testPassword", "encoded-password");
        verify(jwtUtils, times(1)).createAccessToken(findUser);
        verify(jwtUtils, times(1)).createRefreshToken(findUser);
        verify(jwtUtils, times(1)).getAccessTokenTtlMillis();
        verify(jwtUtils, times(1)).getRefreshTokenTtlMillis();
        verify(redisTokenStore, times(1)).saveAccessToken(findUser.getUserId(), "accessToken", 1000L);
        verify(redisTokenStore, times(1)).saveRefreshToken(findUser.getUserId(), "refreshToken", 2000L);
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
        assertThatThrownBy(() -> authService.login(requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtils, never()).createAccessToken(any(User.class));
        verify(jwtUtils, never()).createRefreshToken(any(User.class));
        verify(jwtUtils, never()).getAccessTokenTtlMillis();
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
        verify(redisTokenStore, never()).saveAccessToken(anyLong(), anyString(), anyLong());
        verify(redisTokenStore, never()).saveRefreshToken(anyLong(), anyString(), anyLong());
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
        assertThatThrownBy(() -> authService.login(requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtils, never()).createAccessToken(any(User.class));
        verify(jwtUtils, never()).createRefreshToken(any(User.class));
        verify(jwtUtils, never()).getAccessTokenTtlMillis();
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
        verify(redisTokenStore, never()).saveAccessToken(anyLong(), anyString(), anyLong());
        verify(redisTokenStore, never()).saveRefreshToken(anyLong(), anyString(), anyLong());
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
        assertThatThrownBy(() -> authService.login(requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);

        verify(userRepository, times(1)).findByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches(requestDto.getPassword(), findUser.getPassword());
        verify(jwtUtils, never()).createAccessToken(any(User.class));
        verify(jwtUtils, never()).createRefreshToken(any(User.class));
        verify(jwtUtils, never()).getAccessTokenTtlMillis();
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
        verify(redisTokenStore, never()).saveAccessToken(anyLong(), anyString(), anyLong());
        verify(redisTokenStore, never()).saveRefreshToken(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("로그인 여부 확인 성공")
    void login_status_check_success() {
        // given
        LoginStatusRequestDto requestDto = new LoginStatusRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");

        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);

        when(userRepository.findByEmail(requestDto.getEmail())).thenReturn(Optional.of(findUser));

        when(redisTokenStore.hasRefreshToken(findUser.getUserId())).thenReturn(true);

        // when
        LoginStatusResponseDto responseDto = authService.isLoggedIn(requestDto);

        // then
        assertThat(responseDto.getIsLoggedIn()).isTrue();

        verify(userRepository, times(1)).findByEmail(requestDto.getEmail());
        verify(redisTokenStore, times(1)).hasRefreshToken(findUser.getUserId());
    }
}