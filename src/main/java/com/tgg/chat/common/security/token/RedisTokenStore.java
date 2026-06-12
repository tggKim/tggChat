package com.tgg.chat.common.security.token;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisTokenStore {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String RT_PREFIX = "RT:";
    private static final String USER_SESSIONS_PREFIX = "USER_SESSIONS:";
    private static final int MAX_SESSION_PER_USER = 10;

    public void saveRefreshToken(Long userId, String sid, String refreshToken, long ttlMilliseconds) {
        String refreshTokenKey = createRefreshTokenKey(sid);
        String userSessionsKey = createUserSessionsKey(userId);

        redisTemplate.opsForValue().set(refreshTokenKey, refreshToken, ttlMilliseconds, TimeUnit.MILLISECONDS);
        redisTemplate.opsForZSet().add(userSessionsKey, sid, System.currentTimeMillis());

        removeOldSessions(userSessionsKey);
    }

    public boolean matchesRefreshToken(String sid, String refreshToken) {
        String key = createRefreshTokenKey(sid);
        String redisRefreshToken = redisTemplate.opsForValue().get(key);
        if(redisRefreshToken == null || !redisRefreshToken.equals(refreshToken)) {
            return false;
        }

        return true;
    }

    public void deleteRefreshToken(Long userId, String sid) {
        String refreshTokenKey = createRefreshTokenKey(sid);
        String userSessionsKey = createUserSessionsKey(userId);

        redisTemplate.delete(refreshTokenKey);
        redisTemplate.opsForZSet().remove(userSessionsKey, sid);
    }

    private void removeOldSessions(String userSessionsKey) {
        Long userSessionsCount = redisTemplate.opsForZSet().zCard(userSessionsKey);
        if(userSessionsCount == null || userSessionsCount <= MAX_SESSION_PER_USER) {
            return;
        }

        long overflowCount = userSessionsCount - MAX_SESSION_PER_USER;
        Set<String> oldSids = redisTemplate.opsForZSet().range(userSessionsKey, 0, overflowCount - 1);
        if(oldSids == null || oldSids.isEmpty()) {
            return;
        }

        List<String> oldRefreshTokenKeys = oldSids
                .stream()
                .map(this::createRefreshTokenKey)
                .toList();

        redisTemplate.delete(oldRefreshTokenKeys);
        redisTemplate.opsForZSet().remove(userSessionsKey, oldSids.toArray());
    }

    private String createRefreshTokenKey(String sid) {
        return RT_PREFIX + sid;
    }

    private String createUserSessionsKey(Long userId) {
        return USER_SESSIONS_PREFIX + userId;
    }
}
