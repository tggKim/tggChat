package com.tgg.chat.common.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.jwt.JwtUtils;
import com.tgg.chat.common.security.SecurityWhitelist;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import com.tgg.chat.exception.ErrorResponse;

import io.jsonwebtoken.Claims;
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

	private final JwtUtils jwtUtils;
	private final ObjectMapper objectMapper;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		
		String jwtString = null;

		// 토큰 검증
		try {
			
			String bearerString = request.getHeader("Authorization");

			if(bearerString == null) {
				throw new ErrorException(ErrorCode.JWT_MISSING_AUTH_HEADER);
			}

			if(!bearerString.startsWith("Bearer ")) {
				throw new ErrorException(ErrorCode.JWT_INVALID_AUTH_SCHEME);
			}

			jwtString = bearerString.substring(7);

			jwtUtils.validateToken(jwtString);
		
		} catch(ErrorException e) {
			
			ErrorCode errorCode = e.getErrorCode();
			
	        log.warn("[ValidationError] code={}, status={}, message={}",
	                errorCode.getCode(),
	                errorCode.getStatus().value(),
	                errorCode.getMessage());
			
			makeErrorResposne(errorCode, response);
			return;
			
		}
		
		// claims 추출
		Claims claims = jwtUtils.getClaims(jwtString);
		
		// 권한 생성(현재는 권한이 한개 이므로, 추후에는 엔티티에 권한 넣을 예정)
		List<GrantedAuthority> authorities =List.of(new SimpleGrantedAuthority("USER"));
		
		// Authentication 객체 생성
		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(claims.getSubject(), claims, authorities);
		
		// SecurityContext에 인증 정보 저장
		SecurityContextHolder.getContext().setAuthentication(authenticationToken);
		
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
	private void makeErrorResposne(ErrorCode errorCode, HttpServletResponse response) throws IOException {
		
		ErrorResponse errorResponse = ErrorResponse.of(errorCode);
		
		String jsonResponse = objectMapper.writeValueAsString(errorResponse);
		
		response.setStatus(errorResponse.getStatus());
		response.setContentType("applicaiton/json; charset=UTF-8");
		response.getWriter().write(jsonResponse);
		
	}

}
