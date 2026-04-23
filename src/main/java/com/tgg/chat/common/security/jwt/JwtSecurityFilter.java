package com.tgg.chat.common.security.jwt;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.config.SecurityWhitelist;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import com.tgg.chat.exception.ErrorResponse;

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
	private final ObjectMapper objectMapper;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();
	private final AccessTokenAuthenticator accessTokenAuthenticator;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

		// 토큰 검증
		try {
			String bearerString = request.getHeader("Authorization");
			AuthenticatedUser authenticatedUser = accessTokenAuthenticator.authenticateBearerToken(bearerString);
			
			// Authentication 객체 생성(현재는 권한이 없어서 빈 리스트)
			UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
			
			// SecurityContext에 인증 정보 저장
			SecurityContextHolder.getContext().setAuthentication(authenticationToken);
		} catch(ErrorException errorException) {
			makeErrorResponse(errorException, response);
			return;
		}
		
		// Security FilterChain의 다음 필터로 요청 전달
		filterChain.doFilter(request, response);
	}
	
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		String path = request.getRequestURI();
		String httpMethod = request.getMethod();
		
		return SecurityWhitelist.WHITELIST.stream()
			.anyMatch(permitRule -> permitRule.getHttpMethod().matches(httpMethod) && pathMatcher.match(permitRule.getPattern(), path));
	}
	
	// 응답에 ErrorResponse 담는 메서드
	private void makeErrorResponse(ErrorException errorException, HttpServletResponse response) throws IOException {
		ErrorCode errorCode = errorException.getErrorCode();
		
        log.warn("[ValidationError] code={}, status={}, message={}",
                errorCode.getCode(),
                errorCode.getStatus().value(),
                errorCode.getMessage());
		
		ErrorResponse errorResponse = ErrorResponse.of(errorCode);
		
		String jsonResponse = objectMapper.writeValueAsString(errorResponse);
		
		response.setStatus(errorResponse.getStatus());
		response.setContentType("application/json; charset=UTF-8");
		response.getWriter().write(jsonResponse);
	}

}
