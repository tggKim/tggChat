package com.tgg.chat.common.security.token;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisTokenStore {

	private final RedisTemplate<String, String> redisTemplate;
	
	private static final String AT_PREFIX = "AT:";
	private static final String RT_PREFIX = "RT:";
	
	public void saveAccessToken(Long userId, String accessToken, Long ttlMilliseconds) {
		
		String key = createAccessTokenKey(userId);
		
		redisTemplate.opsForValue().set(key, accessToken, ttlMilliseconds, TimeUnit.MILLISECONDS);
		
	}
	
	public void saveRefreshToken(Long userId, String refreshToken, Long ttlMilliseconds) {
		
		String key = createRefreshTokenKey(userId);
		
		redisTemplate.opsForValue().set(key, refreshToken, ttlMilliseconds, TimeUnit.MILLISECONDS);
		
	}

	public boolean matchesAccessToken(Long userId, String accessToken) {
		String key = createAccessTokenKey(userId);
		String redisAccessToken = redisTemplate.opsForValue().get(key);
		
		if(redisAccessToken == null || !redisAccessToken.equals(accessToken)) {
			return false;
		}
		
		return true;
	}
	
	public boolean hasRefreshToken(Long userId) {
		String key = createRefreshTokenKey(userId);	
		String redisRefreshToken = redisTemplate.opsForValue().get(key);
		return redisRefreshToken != null ? true : false;
	}
	
	public boolean matchesRefreshToken(Long userId, String refreshToken) {
		String key = createRefreshTokenKey(userId);	
		String redisRefreshToken = redisTemplate.opsForValue().get(key);
		if(redisRefreshToken == null || !redisRefreshToken.equals(refreshToken)) {
			return false;
		}
		
		return true;
	}
	
	public void deleteAccessToken(Long userId) {
		
		String key = createAccessTokenKey(userId);
		
		redisTemplate.delete(key);
		
	}
	
	public void deleteRefreshToken(Long userId) {
		
		String key = createRefreshTokenKey(userId);
		
		redisTemplate.delete(key);
		
	}
	
	private String createAccessTokenKey(Long userId) {
		
		String key = AT_PREFIX + userId;	
		
		return key;
		
	}
	
	private String createRefreshTokenKey(Long userId) {
		
		String key = RT_PREFIX + userId;	
		
		return key;
		
	}
	
}
