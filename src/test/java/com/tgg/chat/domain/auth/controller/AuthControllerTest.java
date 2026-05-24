package com.tgg.chat.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import com.tgg.chat.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
}