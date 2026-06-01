package com.tgg.chat.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.AccessTokenAuthenticator;
import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.domain.auth.controller.AuthController;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class})
class SecurityConfigTest {
    @Autowired
    MockMvc mockMvc;

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
        verify(authService, never()).logout(anyLong());
    }
}