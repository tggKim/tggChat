package com.tgg.chat.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.request.LoginStatusRequestDto;
import com.tgg.chat.domain.auth.dto.response.LoginStatusResponseDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import com.tgg.chat.domain.auth.service.AuthService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    JwtUtils jwtUtils;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtSecurityFilter jwtSecurityFilter;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("로그인 API 성공")
    void login_api_success() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "testPassword"
        );

        TokenPair tokenPair = TokenPair.of("accessToken", "refreshToken");
        when(authService.login(any(LoginRequestDto.class))).thenReturn(tokenPair);

        when(jwtUtils.getRefreshTokenTtlMillis()).thenReturn(2000L);

        // when & then
        mockMvc.perform(post("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andExpect(cookie().value("refreshToken", "refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken",true))
                .andExpect(cookie().secure("refreshToken", false))
                .andExpect(cookie().sameSite("refreshToken", "Lax"))
                .andExpect(cookie().path("refreshToken", "/"))
                .andExpect(cookie().maxAge("refreshToken", 2));

        ArgumentCaptor<LoginRequestDto> argumentCaptor = ArgumentCaptor.forClass(LoginRequestDto.class);
        verify(authService, times(1)).login(argumentCaptor.capture());
        LoginRequestDto loginRequestDto = argumentCaptor.getValue();

        assertThat(loginRequestDto.getEmail()).isEqualTo("test@test.com");
        assertThat(loginRequestDto.getPassword()).isEqualTo("testPassword");

        verify(jwtUtils, times(1)).getRefreshTokenTtlMillis();
    }

    @Test
    @DisplayName("로그인 API 실패 - 잘못된 이메일 형식")
    void login_api_fail_invalid_email_format() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "notEmail",
                "password", "testPassword"
        );

        // when & then
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("올바른 이메일 형식이 아닙니다."));

        verify(authService, never()).login(any(LoginRequestDto.class));
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
    }

    @Test
    @DisplayName("로그인 API 실패 - 이메일 미입력")
    void login_api_fail_blank_email() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "",
                "password", "testPassword"
        );

        // when & then
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이메일은 필수입니다."));

        verify(authService, never()).login(any(LoginRequestDto.class));
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
    }

    @Test
    @DisplayName("로그인 API 실패 - 이메일 길이 초과")
    void login_api_fail_email_too_long() throws Exception {
        // given
        String longEmail =
                "a".repeat(64) + "@" +
                        "b".repeat(63) + "." +
                        "c".repeat(63) + "." +
                        "d".repeat(63) + ".com";

        Map<String, Object> requestBody = Map.of(
                "email", longEmail,
                "password", "testPassword"
        );

        // when & then
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이메일 길이는 254자 이하입니다."));

        verify(authService, never()).login(any(LoginRequestDto.class));
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
    }

    @Test
    @DisplayName("로그인 API 실패 - 비밀번호 미입력")
    void login_api_fail_blank_password() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", ""
        );

        // when & then
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("비밀번호는 필수입니다."));

        verify(authService, never()).login(any(LoginRequestDto.class));
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
    }

    @Test
    @DisplayName("로그인 API 실패 - 존재하지 않는 유저, 삭제된 유저")
    void login_api_fail_user_not_found_or_deleted_user() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "testPassword"
        );

        when(authService.login(any(LoginRequestDto.class))).thenThrow(new ErrorException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("U003"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."));

        verify(authService, times(1)).login(any(LoginRequestDto.class));
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
    }

    @Test
    @DisplayName("로그인 API 실패 - 잘못된 비밀번호")
    void login_api_fail_invalid_password() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "wrongPassword"
        );

        when(authService.login(any(LoginRequestDto.class)))
                .thenThrow(new ErrorException(ErrorCode.INVALID_PASSWORD));

        // when & then
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("U004"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("비밀번호가 일치하지 않습니다."));

        verify(authService, times(1)).login(any(LoginRequestDto.class));
        verify(jwtUtils, never()).getRefreshTokenTtlMillis();
    }

    @Test
    @DisplayName("로그인 여부 API 성공")
    void login_status_api_success() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com"
        );

        LoginStatusResponseDto responseDto = LoginStatusResponseDto.of(true);

        when(authService.isLoggedIn(any(LoginStatusRequestDto.class))).thenReturn(responseDto);

        // when & then
        mockMvc.perform(post("/login-status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isLoggedIn").value(true));

        ArgumentCaptor<LoginStatusRequestDto> argumentCaptor = ArgumentCaptor.forClass(LoginStatusRequestDto.class);
        verify(authService, times(1)).isLoggedIn(argumentCaptor.capture());
        LoginStatusRequestDto loginStatusRequestDto = argumentCaptor.getValue();

        assertThat(loginStatusRequestDto.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("로그인 여부 API 실패 - 잘못된 이메일 형식")
    void login_status_api_fail_invalid_email_format() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "notEmail"
        );

        // when & then
        mockMvc.perform(post("/login-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("올바른 이메일 형식이 아닙니다."));

        verify(authService, never()).isLoggedIn(any(LoginStatusRequestDto.class));
    }

    @Test
    @DisplayName("로그인 여부 API 실패 - 이메일 미입력")
    void login_status_api_fail_blank_email() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", ""
        );

        // when & then
        mockMvc.perform(post("/login-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이메일은 필수입니다."));

        verify(authService, never()).isLoggedIn(any(LoginStatusRequestDto.class));
    }

    @Test
    @DisplayName("로그인 여부 API 실패 - 이메일 길이 초과")
    void login_status_api_fail_email_too_long() throws Exception {
        // given
        String longEmail =
                "a".repeat(64) + "@" +
                        "b".repeat(63) + "." +
                        "c".repeat(63) + "." +
                        "d".repeat(63) + ".com";

        Map<String, Object> requestBody = Map.of(
                "email", longEmail
        );

        // when & then
        mockMvc.perform(post("/login-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이메일 길이는 254자 이하입니다."));

        verify(authService, never()).isLoggedIn(any(LoginStatusRequestDto.class));
    }

    @Test
    @DisplayName("로그인 여부 API 실패 - 존재하지 않는 유저, 삭제된 유저")
    void login_status_api_fail_user_not_found_or_deleted_user() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com"
        );

        when(authService.isLoggedIn(any(LoginStatusRequestDto.class))).thenThrow(new ErrorException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/login-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("U003"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."));

        ArgumentCaptor<LoginStatusRequestDto> argumentCaptor = ArgumentCaptor.forClass(LoginStatusRequestDto.class);
        verify(authService, times(1)).isLoggedIn(argumentCaptor.capture());
        LoginStatusRequestDto loginStatusRequestDto = argumentCaptor.getValue();

        assertThat(loginStatusRequestDto.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("로그아웃 api 성공")
    void logout_api_success() throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        // when & then
        try {
            mockMvc.perform(post("/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(authService, times(1)).logout(1L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}