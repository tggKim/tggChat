package com.tgg.chat.common.security.principal;

import lombok.Getter;

@Getter
public class AuthenticatedUser {
    private final Long userId;
    private final String email;
    private final String username;
    public AuthenticatedUser(Long userId, String email, String username) {
        this.userId = userId;
        this.email = email;
        this.username = username;
    }
}
