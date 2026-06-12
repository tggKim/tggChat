package com.tgg.chat.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.AccessTokenAuthenticator;
import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.auth.controller.AuthController;
import com.tgg.chat.domain.auth.dto.request.LoginRequestDto;
import com.tgg.chat.domain.auth.dto.response.TokenPair;
import com.tgg.chat.domain.auth.service.AuthService;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class})
class SecurityConfigTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    AccessTokenAuthenticator accessTokenAuthenticator;

    @MockitoBean
    JwtUtils jwtUtils;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("보안 설정 실패 - 보호 API는 Authorization 헤더가 없으면 401 응답")
    void protected_api_without_authorization_header_returns_unauthorized() throws Exception {
        // given
        when(accessTokenAuthenticator.authenticateBearerToken(null)).thenThrow(new ErrorException(ErrorCode.JWT_MISSING_AUTH_HEADER));

        // when & then
        mockMvc.perform(post("/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.code").value(ErrorCode.JWT_MISSING_AUTH_HEADER.getCode()))
                .andExpect(jsonPath("$.status").value(ErrorCode.JWT_MISSING_AUTH_HEADER.getStatus().value()))
                .andExpect(jsonPath("$.message").value(ErrorCode.JWT_MISSING_AUTH_HEADER.getMessage()));

        verify(accessTokenAuthenticator, times(1)).authenticateBearerToken(null);
        verify(authService, never()).logout(anyLong(), anyString());
    }

    @Test
    @DisplayName("보안 설정 성공 - 보호 API는 JWT 인증 성공 시 컨트롤러까지 도달")
    void protected_api_with_valid_jwt_reaches_controller() throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        when(accessTokenAuthenticator.authenticateBearerToken("Bearer Token")).thenReturn(authenticatedUser);

        // when & then
        mockMvc.perform(post("/logout")
                    .header("Authorization", "Bearer Token"))
                .andExpect(status().isOk());

        verify(accessTokenAuthenticator, times(1)).authenticateBearerToken("Bearer Token");
        verify(authService, times(1)).logout(1L, "sid");
    }

    @Test
    @DisplayName("보안 설정 성공 - 공개 API는 JWT 필터를 거치지 않는다")
    void public_api_does_not_use_jwt_filter() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "email", "test@test.com",
                "password", "testPassword"
        );

        TokenPair tokenPair = TokenPair.of("accessToken", "refreshToken");

        when(authService.login(any(LoginRequestDto.class), any())).thenReturn(tokenPair);
        when(jwtUtils.getRefreshTokenTtlMillis()).thenReturn(Duration.ofMinutes(10).toMillis());

        // when & then
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", false))
                .andExpect(cookie().sameSite("refreshToken", "Lax"))
                .andExpect(cookie().path("refreshToken", "/"))
                .andExpect(cookie().maxAge("refreshToken", 600))
                .andExpect(cookie().value("refreshToken", "refreshToken"));

        verify(accessTokenAuthenticator, never()).authenticateBearerToken(any());
        verify(authService, times(1)).login(any(LoginRequestDto.class), any());
        verify(jwtUtils, times(1)).getRefreshTokenTtlMillis();
    }
}