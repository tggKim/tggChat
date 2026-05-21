package com.tgg.chat.common.security.jwt;

import java.io.IOException;
import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.exception.ErrorException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecurityFilter extends OncePerRequestFilter{
	private final AccessTokenAuthenticator accessTokenAuthenticator;
	private final ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		AuthenticatedUser authenticatedUser;
		
		// 토큰 검증
		try {
			String bearerString = request.getHeader("Authorization");
			authenticatedUser = accessTokenAuthenticator.authenticateBearerToken(bearerString);
		} catch(ErrorException errorException) {
            ErrorCode errorCode = errorException.getErrorCode();

            log.warn("[AuthenticationError] code={}, status={}, message={}",
                    errorCode.getCode(),
                    errorCode.getStatus().value(),
                    errorCode.getMessage());

            ErrorResponse errorResponse = ErrorResponse.of(errorCode);

            response.setStatus(errorCode.getStatus().value());
            response.setContentType("application/json; charset=UTF-8");

            objectMapper.writeValue(response.getWriter(), errorResponse);

            return;
		}
		
		// Authentication 객체 생성(현재는 권한이 없어서 빈 리스트)
		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
		
		// SecurityContext에 인증 정보 저장
		SecurityContextHolder.getContext().setAuthentication(authenticationToken);
		
		// Security FilterChain의 다음 필터로 요청 전달
		filterChain.doFilter(request, response);
	}
}
