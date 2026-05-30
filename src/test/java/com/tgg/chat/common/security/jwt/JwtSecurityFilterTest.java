package com.tgg.chat.common.security.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtSecurityFilterTest {
    @Mock
    FilterChain filterChain;

    @Mock
    AccessTokenAuthenticator accessTokenAuthenticator;

    ObjectMapper objectMapper;

    JwtSecurityFilter jwtSecurityFilter;

    @BeforeEach
    void setFilter() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        jwtSecurityFilter = new JwtSecurityFilter(accessTokenAuthenticator, objectMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("JWT 필터 성공 - SecurityContext에 인증 정보 저장")
    void jwtSecurityFilter_success() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer AccessToken");

        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");

        when(accessTokenAuthenticator.authenticateBearerToken("Bearer AccessToken")).thenReturn(authenticatedUser);

        // when
        jwtSecurityFilter.doFilter(request, response, filterChain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).isEmpty();

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(1L);
        assertThat(principal.getEmail()).isEqualTo("test@test.com");
        assertThat(principal.getUsername()).isEqualTo("testUsername");

        verify(accessTokenAuthenticator, times(1)).authenticateBearerToken("Bearer AccessToken");
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("JWT 필터 실패 - 인증 예외 발생")
    void jwtSecurityFilter_fail_authentication_exception() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "invalidToken");

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(accessTokenAuthenticator.authenticateBearerToken("invalidToken")).thenThrow(new ErrorException(ErrorCode.JWT_INVALID_TOKEN));

        // when
        jwtSecurityFilter.doFilter(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        assertThat(response.getStatus()).isEqualTo(ErrorCode.JWT_INVALID_TOKEN.getStatus().value());
        assertThat(response.getContentType()).isEqualTo("application/json; charset=UTF-8");

        JsonNode jsonNode = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(jsonNode.get("code").asText()).isEqualTo(ErrorCode.JWT_INVALID_TOKEN.getCode());
        assertThat(jsonNode.get("status").asInt()).isEqualTo(ErrorCode.JWT_INVALID_TOKEN.getStatus().value());
        assertThat(jsonNode.get("message").asText()).isEqualTo(ErrorCode.JWT_INVALID_TOKEN.getMessage());
        assertThat(jsonNode.get("timestamp").asText()).isNotBlank();

        verify(accessTokenAuthenticator, times(1)).authenticateBearerToken("invalidToken");
        verify(filterChain, never()).doFilter(request, response);
    }
}