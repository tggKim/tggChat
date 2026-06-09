package com.tgg.chat.common.security.token;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisTokenStore {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String RT_PREFIX = "RT:";

    public void saveRefreshToken(String sid, String refreshToken, long ttlMilliseconds) {
        String key = createRefreshTokenKey(sid);
        redisTemplate.opsForValue().set(key, refreshToken, ttlMilliseconds, TimeUnit.MILLISECONDS);
    }

    public boolean matchesRefreshToken(String sid, String refreshToken) {
        String key = createRefreshTokenKey(sid);
        String redisRefreshToken = redisTemplate.opsForValue().get(key);
        if(redisRefreshToken == null || !redisRefreshToken.equals(refreshToken)) {
            return false;
        }

        return true;
    }

    public void deleteRefreshToken(String sid) {
        String refreshTokenKey = createRefreshTokenKey(sid);
        redisTemplate.delete(refreshTokenKey);
    }

    private String createRefreshTokenKey(String sid) {
        return RT_PREFIX + sid;
    }

}
