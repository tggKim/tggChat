package com.tgg.chat.common.filter;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.jwt.JwtUtils;
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
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		
		String bearerString = request.getHeader("Authorization");
		
		// 토큰이 없거나 Bearer 형식이 아니면 인증 절차 X -> 다음 필터로 넘긴다
		if(bearerString == null || !bearerString.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}
		
		String jwtString = bearerString.substring(7);
		
		// 토큰 검증
		try {
			
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
		
		// Authentication 객체 생성
		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(claims.getSubject(), claims);
		
		System.out.println(authenticationToken);
		
		// SecurityContext에 인증 정보 저장
		SecurityContextHolder.getContext().setAuthentication(authenticationToken);
		
		// Security FilterChain의 다음 필터로 요청 전달
		filterChain.doFilter(request, response);
		
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
