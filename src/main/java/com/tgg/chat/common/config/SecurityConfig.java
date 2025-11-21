package com.tgg.chat.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.tgg.chat.common.filter.JwtSecurityFilter;
import com.tgg.chat.common.security.SecurityWhitelist;
import com.tgg.chat.common.security.SecurityWhitelist.PermitRule;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	
	private final JwtSecurityFilter jwtSecurityFilter;
	
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		
		http.csrf(csrf -> csrf.disable())
		.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		.addFilterBefore(jwtSecurityFilter, UsernamePasswordAuthenticationFilter.class)
		.formLogin(form -> form.disable())
		.httpBasic(basic -> basic.disable())
		.logout(logout -> logout.disable());
		
		http.authorizeHttpRequests(auth -> {
			
			// 화이트 리스트에 대해서 누구나 접근 가능하게 설정
			for(PermitRule permitRule : SecurityWhitelist.WHITELIST) {
				auth.requestMatchers(permitRule.getHttpMethod(), permitRule.getPattern()).permitAll();
			}
			
			// 화이트 리스트 이외의 요청들은 인증된 사용자만 접근 가능하게 설정
			auth.anyRequest().authenticated();
			
		});
		
		return http.build();
		
	}
	
}
