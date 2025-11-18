package com.tgg.chat.common.security;

import java.util.List;

import org.springframework.stereotype.Component;

public class SecurityWhitelist {
	
	public static final String[] WHITELIST = {
			"/swagger-ui/**",
			"/v3/api-docs/**",
			"/user/**",
			"/login"};
	
}
