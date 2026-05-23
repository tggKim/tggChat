package com.tgg.chat.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        String longEmail = "a".repeat(249) + "@a.com";

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
}