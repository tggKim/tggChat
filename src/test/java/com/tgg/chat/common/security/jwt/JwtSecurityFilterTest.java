package com.tgg.chat.common.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
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
}