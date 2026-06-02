package com.tgg.chat.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.request.UserUpdateRequestDto;
import com.tgg.chat.domain.user.dto.response.OtherUserResponseDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.dto.response.UserResponseDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.service.UserService;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtSecurityFilter jwtSecurityFilter;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("회원가입 api 성공")
    void signup_api_success() throws Exception {
        // given
        Map<String, Object> requestMap = Map.of(
                "email", "test@test.com",
                "password", "testPassword",
                "username", "testUsername"
        );

        User savedUser = User.of("test@test.com", "encodedPassword", "testUsername");
        LocalDateTime testTime = LocalDateTime.of(2026, 5, 23, 12, 0, 0);
        ReflectionTestUtils.setField(savedUser, "userId", 1L);
        ReflectionTestUtils.setField(savedUser, "createdAt", testTime);
        ReflectionTestUtils.setField(savedUser, "updatedAt", testTime);
        SignUpResponseDto responseDto = SignUpResponseDto.of(savedUser);

        when(userService.signUpUser(any(SignUpRequestDto.class))).thenReturn(responseDto);

        // when & then
        mockMvc.perform(
                post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMap)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.username").value("testUsername"))
                .andExpect(jsonPath("$.createdAt").value("2026-05-23 12:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-05-23 12:00:00"));

        ArgumentCaptor<SignUpRequestDto> argumentCaptor = ArgumentCaptor.forClass(SignUpRequestDto.class);
        verify(userService, times(1)).signUpUser(argumentCaptor.capture());

        SignUpRequestDto captorValue = argumentCaptor.getValue();
        assertThat(captorValue.getEmail()).isEqualTo("test@test.com");
        assertThat(captorValue.getPassword()).isEqualTo("testPassword");
        assertThat(captorValue.getUsername()).isEqualTo("testUsername");
    }

    @Test
    @DisplayName("회원가입 API 실패 - 잘못된 이메일 형식")
    void signup_api_fail_invalid_email_format() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "notEmail",
                "password", "testPassword",
                "username", "testUsername"
        );

        // when & then
        mockMvc.perform(
                post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("올바른 이메일 형식이 아닙니다."));

        verify(userService, never()).signUpUser(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("회원가입 API 실패 - 이메일 미입력")
    void signup_api_fail_blank_email() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "",
                "password", "testPassword",
                "username", "testUsername"
        );

        // when & then
        mockMvc.perform(
                        post("/user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이메일은 필수입니다."));

        verify(userService, never()).signUpUser(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("회원가입 API 실패 - 이메일 길이 초과")
    void signup_api_fail_email_too_long() throws Exception {
        // given
        String longEmail =
                "a".repeat(64) + "@" +
                        "b".repeat(63) + "." +
                        "c".repeat(63) + "." +
                        "d".repeat(63) + ".com";

        Map<String, Object> requestBody = Map.of(
                "email", longEmail,
                "password", "testPassword",
                "username", "testUsername"
        );

        // when & then
        mockMvc.perform(
                        post("/user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이메일 길이는 254자 이하입니다."));

        verify(userService, never()).signUpUser(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("회원가입 API 실패 - 비밀번호 미입력")
    void signup_api_fail_blank_password() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "",
                "username", "testUsername"
        );

        // when & then
        mockMvc.perform(
                        post("/user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("비밀번호는 필수입니다."));

        verify(userService, never()).signUpUser(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("회원가입 API 실패 - 유저명 미입력")
    void signup_api_fail_blank_username() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "testPassword",
                "username", ""
        );

        // when & then
        mockMvc.perform(
                        post("/user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사용자명은 필수입니다."));

        verify(userService, never()).signUpUser(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("회원가입 API 실패 - 유저명 길이 초과")
    void signup_api_fail_username_too_long() throws Exception {
        // given
        String longUsername = "a".repeat(51);

        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "testPassword",
                "username", longUsername
        );

        // when & then
        mockMvc.perform(
                        post("/user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사용자명 길이는 50자 이하입니다."));

        verify(userService, never()).signUpUser(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("회원가입 API 실패 - 중복된 이메일")
    void signup_api_fail_duplicate_email() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "testPassword",
                "username", "testUsername"
        );

        when(userService.signUpUser(any(SignUpRequestDto.class))).thenThrow(new ErrorException(ErrorCode.DUPLICATE_EMAIL_ERROR));

        // when & then
        mockMvc.perform(
                post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("U001"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("중복된 이메일 입니다."));

        verify(userService, times(1)).signUpUser(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("회원가입 API 실패 - 중복된 유저명")
    void signup_api_fail_duplicate_username() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "testPassword",
                "username", "testUsername"
        );

        when(userService.signUpUser(any(SignUpRequestDto.class))).thenThrow(new ErrorException(ErrorCode.DUPLICATE_USERNAME_ERROR));

        // when & then
        mockMvc.perform(
                        post("/user")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("U002"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("중복된 유저명 입니다."));

        verify(userService, times(1)).signUpUser(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("타 회원 조회 API 성공")
    void find_other_user_api_success() throws Exception {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);
        LocalDateTime localDateTime = LocalDateTime.of(2026, 12, 1, 9, 0, 0);
        ReflectionTestUtils.setField(findUser, "createdAt", localDateTime);
        ReflectionTestUtils.setField(findUser, "updatedAt", localDateTime);

        OtherUserResponseDto responseDto = OtherUserResponseDto.of(findUser);

        when(userService.findOtherUser(1L)).thenReturn(responseDto);

        // when & then
        mockMvc.perform(get("/user/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.username").value("testUsername"))
                .andExpect(jsonPath("$.createdAt").value("2026-12-01 09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-12-01 09:00:00"));

        verify(userService, times(1)).findOtherUser(1L);
    }

    @Test
    @DisplayName("타 회원 조회 API 실패 - 존재하지 않거나 삭제된 유저")
    void find_other_user_api_fail_not_found_or_deleted_user() throws Exception {
        // given
        when(userService.findOtherUser(1L)).thenThrow(new ErrorException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/user/1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("U003"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."));

        verify(userService, times(1)).findOtherUser(1L);
    }

    @Test
    @DisplayName("본인 회원 조회 API 성공")
    void find_user_api_success() throws Exception {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);
        LocalDateTime localDateTime = LocalDateTime.of(2026, 12, 1, 9, 0, 0);
        ReflectionTestUtils.setField(findUser, "createdAt", localDateTime);
        ReflectionTestUtils.setField(findUser, "updatedAt", localDateTime);

        UserResponseDto responseDto = UserResponseDto.of(findUser);

        when(userService.findUser(1L)).thenReturn(responseDto);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        // when & then
        try {
            mockMvc.perform(get("/me"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.userId").value(1L))
                    .andExpect(jsonPath("$.email").value("test@test.com"))
                    .andExpect(jsonPath("$.username").value("testUsername"))
                    .andExpect(jsonPath("$.createdAt").value("2026-12-01 09:00:00"))
                    .andExpect(jsonPath("$.updatedAt").value("2026-12-01 09:00:00"));

            verify(userService, times(1)).findUser(1L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("본인 회원 조회 API 실패 - 존재하지 않거나 삭제된 유저")
    void find_user_api_fail_not_found_or_deleted_user() throws Exception {
        // given
        when(userService.findUser(1L)).thenThrow(new ErrorException(ErrorCode.USER_NOT_FOUND));

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        // when & then
        try {
            mockMvc.perform(get("/me"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("U003"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."));

            verify(userService, times(1)).findUser(1L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("회원 수정 API 성공")
    void update_user_api_success() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "username", "updateUsername"
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        // when & then
        try {
            mockMvc.perform(patch("/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            ArgumentCaptor<UserUpdateRequestDto> argumentCaptor = ArgumentCaptor.forClass(UserUpdateRequestDto.class);
            verify(userService, times(1)).updateUser(eq(1L), argumentCaptor.capture());
            UserUpdateRequestDto userUpdateRequestDto = argumentCaptor.getValue();

            assertThat(userUpdateRequestDto.getUsername()).isEqualTo("updateUsername");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("회원 수정 API 실패 - 빈 사용자명")
    void update_user_api_fail_blank_username() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "username", ""
        );

        // when & then
        mockMvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사용자명은 필수입니다."));

        verify(userService, never()).updateUser(anyLong(), any(UserUpdateRequestDto.class));
    }

    @Test
    @DisplayName("회원 수정 API 실패 - 사용자명 50자 초과")
    void update_user_api_fail_too_long_username() throws Exception {
        // given
        String username = "a".repeat(51);
        Map<String, Object> requestBody = Map.of(
                "username", username
        );

        // when & then
        mockMvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사용자명 길이는 50자 이하입니다."));

        verify(userService, never()).updateUser(anyLong(), any(UserUpdateRequestDto.class));
    }

    @Test
    @DisplayName("회원 수정 API 실패 - 존재하지 않거나 삭제된 유저")
    void update_user_api_fail_not_found_or_deleted_user() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "username", "updateUsername"
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        doThrow(new ErrorException(ErrorCode.USER_NOT_FOUND)).when(userService).updateUser(eq(1L), any(UserUpdateRequestDto.class));

        // when & then
        try {
            mockMvc.perform(patch("/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("U003"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."));

            ArgumentCaptor<UserUpdateRequestDto> argumentCaptor = ArgumentCaptor.forClass(UserUpdateRequestDto.class);
            verify(userService, times(1)).updateUser(eq(1L), argumentCaptor.capture());
            UserUpdateRequestDto userUpdateRequestDto = argumentCaptor.getValue();

            assertThat(userUpdateRequestDto.getUsername()).isEqualTo("updateUsername");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}