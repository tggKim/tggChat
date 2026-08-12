package com.tgg.chat.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class TokenPair {
    private final String accessToken;
    private final String refreshToken;
    private final String mediaToken;

    private TokenPair(String accessToken, String refreshToken, String mediaToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.mediaToken = mediaToken;
    }

    public static TokenPair of(String accessToken, String refreshToken, String mediaToken) {
        return new TokenPair(accessToken, refreshToken, mediaToken);
    }
}
