package com.tgg.chat.common.security.jwt;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.InsufficientAuthenticationException;
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
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		AuthenticatedUser authenticatedUser;
		
		// 토큰 검증
		try {
			String bearerString = request.getHeader("Authorization");
			authenticatedUser = accessTokenAuthenticator.authenticateBearerToken(bearerString);
		} catch(ErrorException errorException) {
			request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, errorException.getErrorCode());
			jwtAuthenticationEntryPoint.commence(request, response, new InsufficientAuthenticationException(errorException.getMessage()));
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
