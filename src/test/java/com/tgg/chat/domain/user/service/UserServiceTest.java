package com.tgg.chat.domain.user.service;

import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    UserRepository userRepository;

    @Mock
    UserMapper userMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    RedisTokenStore redisTokenStore;

    @InjectMocks
    UserService userService;

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        // given
        SignUpRequestDto requestDto = new SignUpRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");
        ReflectionTestUtils.setField(requestDto, "username", "testUsername");

        when(userRepository.existsByEmail(requestDto.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(requestDto.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(requestDto.getPassword())).thenReturn("encoded-password");

        User savedUser = User.of(requestDto.getEmail(), "encoded-password", requestDto.getUsername());
        ReflectionTestUtils.setField(savedUser, "userId", 1L);
        LocalDateTime localDateTime  = LocalDateTime.now();
        ReflectionTestUtils.setField(savedUser, "createdAt", localDateTime);
        ReflectionTestUtils.setField(savedUser, "updatedAt", localDateTime);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // when
        SignUpResponseDto responseDto = userService.signUpUser(requestDto);

        // then
        assertThat(responseDto.getUserId()).isEqualTo(savedUser.getUserId());
        assertThat(responseDto.getUsername()).isEqualTo(savedUser.getUsername());
        assertThat(responseDto.getCreatedAt()).isEqualTo(savedUser.getCreatedAt());
        assertThat(responseDto.getUpdatedAt()).isEqualTo(savedUser.getUpdatedAt());

        verify(userRepository, times(1)).existsByEmail(requestDto.getEmail());
        verify(userRepository, times(1)).existsByUsername(requestDto.getUsername());
        verify(passwordEncoder, times(1)).encode(requestDto.getPassword());

        ArgumentCaptor<User> argumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(argumentCaptor.capture());

        User captorUser = argumentCaptor.getValue();
        assertThat(captorUser.getEmail()).isEqualTo(requestDto.getEmail());
        assertThat(captorUser.getPassword()).isEqualTo("encoded-password");
        assertThat(captorUser.getUsername()).isEqualTo(requestDto.getUsername());
        assertThat(captorUser.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("회원가입시 이메일 중복으로 실패")
    void signup_fail_duplicated_email() {
        // given
        SignUpRequestDto requestDto = new SignUpRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");
        ReflectionTestUtils.setField(requestDto, "username", "testUsername");

        when(userRepository.existsByEmail(requestDto.getEmail())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signUpUser(requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL_ERROR);

        verify(userRepository, times(1)).existsByEmail(requestDto.getEmail());
        verify(userRepository, never()).existsByUsername(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입시 유저명 중복으로 실패")
    void signup_fail_duplicated_username() {
        // given
        SignUpRequestDto requestDto = new SignUpRequestDto();
        ReflectionTestUtils.setField(requestDto, "email", "test@test.com");
        ReflectionTestUtils.setField(requestDto, "password", "testPassword");
        ReflectionTestUtils.setField(requestDto, "username", "testUsername");

        when(userRepository.existsByEmail(requestDto.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(requestDto.getUsername())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signUpUser(requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_USERNAME_ERROR);

        verify(userRepository, times(1)).existsByEmail(requestDto.getEmail());
        verify(userRepository, times(1)).existsByUsername(requestDto.getUsername());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}