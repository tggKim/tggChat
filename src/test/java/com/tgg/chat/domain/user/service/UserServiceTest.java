package com.tgg.chat.domain.user.service;

import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.request.UserUpdateRequestDto;
import com.tgg.chat.domain.user.dto.response.OtherUserResponseDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.dto.response.UserResponseDto;
import com.tgg.chat.domain.user.entity.User;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

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

    @Test
    @DisplayName("타 유저 조회 성공")
    void find_other_user_success() {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(findUser, "createdAt", now);
        ReflectionTestUtils.setField(findUser, "updatedAt", now);
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when
        OtherUserResponseDto responseDto = userService.findOtherUser(1L);

        // then
        assertThat(responseDto.getUserId()).isEqualTo(1L);
        assertThat(responseDto.getUsername()).isEqualTo("testUsername");
        assertThat(responseDto.getCreatedAt()).isEqualTo(now);
        assertThat(responseDto.getUpdatedAt()).isEqualTo(now);

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("타 유저 조회 실패 - 존재하지 않는 유저")
    void find_other_user_fail_user_not_found() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.findOtherUser(1L))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("타 유저 조회 실패 - 삭제된 유저")
    void find_other_user_fail_deleted_user() {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "deleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when & then
        assertThatThrownBy(() -> userService.findOtherUser(1L))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("본인 유저 조회 성공")
    void find_user_success() {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(findUser, "createdAt", now);
        ReflectionTestUtils.setField(findUser, "updatedAt", now);
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when
        UserResponseDto responseDto = userService.findUser(1L);

        // then
        assertThat(responseDto.getUserId()).isEqualTo(1L);
        assertThat(responseDto.getEmail()).isEqualTo("test@test.com");
        assertThat(responseDto.getUsername()).isEqualTo("testUsername");
        assertThat(responseDto.getCreatedAt()).isEqualTo(now);
        assertThat(responseDto.getUpdatedAt()).isEqualTo(now);

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("본인 유저 조회 실패 - 존재하지 않는 유저")
    void find_user_fail_user_not_found() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.findUser(1L))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("본인 유저 조회 실패 - 삭제된 유저")
    void find_user_fail_deleted_user() {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "deleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when & then
        assertThatThrownBy(() -> userService.findUser(1L))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("유저 업데이트 성공 - 기존과 다른 유저명")
    void update_user_success_changed_username() {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");

        UserUpdateRequestDto requestDto = new UserUpdateRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "updateUsername");

        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        when(userRepository.existsByUsername("updateUsername")).thenReturn(false);

        // when
        userService.updateUser(1L, requestDto);

        // then
        assertThat(findUser.getUsername()).isEqualTo("updateUsername");

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).existsByUsername("updateUsername");
    }

    @Test
    @DisplayName("유저 업데이트 성공 - 기존과 같은 유저명")
    void update_user_success_same_username() {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");

        UserUpdateRequestDto requestDto = new UserUpdateRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "testUsername");

        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when
        userService.updateUser(1L, requestDto);

        // then
        assertThat(findUser.getUsername()).isEqualTo("testUsername");

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("유저 업데이트 실패 - 존재하지 않는 유저")
    void update_user_fail_user_not_found() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        UserUpdateRequestDto requestDto = new UserUpdateRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "updateUsername");

        // when & then
        assertThatThrownBy(() -> userService.updateUser(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("유저 업데이트 실패 - 삭제된 유저")
    void update_user_fail_deleted_user() {
        // given
        User findUser = User.of("test@test.com", "testPassword", "testUsername");
        ReflectionTestUtils.setField(findUser, "deleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        UserUpdateRequestDto requestDto = new UserUpdateRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "updateUsername");

        // when & then
        assertThatThrownBy(() -> userService.updateUser(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("유저 업데이트 실패 - 중복된 유저명")
    void update_user_fail_duplicated_username() {
        // given
        User findUser = User.of("test@test.com", "testPassword", "testUsername");
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        UserUpdateRequestDto requestDto = new UserUpdateRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "updateUsername");

        when(userRepository.existsByUsername("updateUsername")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_USERNAME_ERROR);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).existsByUsername("updateUsername");
    }

    @Test
    @DisplayName("유저 삭제 성공")
    void delete_user_success() {
        // given
        User findUser = User.of("test@test.com", "testPassword", "testUsername");
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when
        userService.deleteUser(1L);

        // then
        assertThat(findUser.getDeleted()).isTrue();

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("유저 삭제 실패 - 존재하지 않는 유저")
    void delete_user_fail_user_not_found() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(1L))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("유저 삭제 실패 - 삭제된 유저")
    void delete_user_fail_deleted_user() {
        // given
        User findUser = User.of("test@test.com", "testPassword", "testUsername");
        ReflectionTestUtils.setField(findUser, "deleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(1L))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
    }
}