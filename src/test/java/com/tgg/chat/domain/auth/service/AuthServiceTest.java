package com.tgg.chat.domain.auth.service;

import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
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
}