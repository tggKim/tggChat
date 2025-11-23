package com.tgg.chat.common.security;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import lombok.Getter;

public class SecurityWhitelist {
	
	public static final List<PermitRule> WHITELIST = List.of(
				new PermitRule(HttpMethod.GET, "/swagger-ui/**"),
				new PermitRule(HttpMethod.GET, "/v3/api-docs/**"),
				new PermitRule(HttpMethod.GET, "/user/**"),
				new PermitRule(HttpMethod.POST, "/user"),
				new PermitRule(HttpMethod.POST, "/login"),
				new PermitRule(HttpMethod.POST, "/login-status"),
				new PermitRule(HttpMethod.POST, "/refresh")
			);
	
	@Getter
	public static class PermitRule {
		
		private final HttpMethod httpMethod;
		private final String pattern;
		
		private PermitRule(HttpMethod httpMethod, String pattern) {
			this.httpMethod = httpMethod;
			this.pattern = pattern;
		}
		
	}
	
}
