package com.tgg.chat.common.filter;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tgg.chat.common.jwt.JwtUtils;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtSecurityFilter extends OncePerRequestFilter{

	private final JwtUtils jwtUtils;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		
		String bearerString = request.getHeader("Authorization");
		
		// 토큰이 없거나 Bearer 형식이 아니면 인증 절차 X -> 다음 필터로 넘긴다
		if(bearerString == null || !bearerString.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
		}
		
		String jwtString = bearerString.substring(7);
		
		// 토큰 검증
		jwtUtils.validateToken(jwtString);
		
		// claims 추출
		Claims claims = jwtUtils.getClaims(jwtString);
		
		// Authentication 객체 생성
		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(claims.getSubject(), claims);
		
		// SecurityContext에 인증 정보 저장
		SecurityContextHolder.getContext().setAuthentication(authenticationToken);
		
		// Security FilterChain의 다음 필터로 요청 전달
		filterChain.doFilter(request, response);
		
	}

}
