package com.tgg.chat.common.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
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
import com.tgg.chat.common.redis.RedisUtils;
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
	private final RedisUtils redisUtils;
	
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
		
		} catch(ErrorException errorException) {
			
			makeErrorResposne(errorException, response);
			
			return;
			
		}
		
		// claims 추출
		Claims claims = jwtUtils.getClaims(jwtString);
		
		// 레디스에 저장된 accessToken 과 비교
		Long userId = Long.parseLong(claims.getSubject());
		String redisAccessToken = redisUtils.getAccessToken(userId);
		if(redisAccessToken == null || !redisAccessToken.equals(jwtString)) {
			
			ErrorException errorException = new ErrorException(ErrorCode.ACCESS_TOKEN_MISMATCH);
			
			makeErrorResposne(errorException, response);
			
			return;
			
		}
		
		// Authentication 객체 생성(현재는 권한이 없어서 빈 리스트)
		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(claims, null, Collections.emptyList());
		
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
	private void makeErrorResposne(ErrorException errorException, HttpServletResponse response) throws IOException {
		
		ErrorCode errorCode = errorException.getErrorCode();
		
        log.warn("[ValidationError] code={}, status={}, message={}",
                errorCode.getCode(),
                errorCode.getStatus().value(),
                errorCode.getMessage());
		
		ErrorResponse errorResponse = ErrorResponse.of(errorCode);
		
		String jsonResponse = objectMapper.writeValueAsString(errorResponse);
		
		response.setStatus(errorResponse.getStatus());
		response.setContentType("applicaiton/json; charset=UTF-8");
		response.getWriter().write(jsonResponse);
		
	}

}
