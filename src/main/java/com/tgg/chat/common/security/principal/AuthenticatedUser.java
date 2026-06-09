package com.tgg.chat.common.security.principal;

import lombok.Getter;

@Getter
public class AuthenticatedUser {
    private final Long userId;
    private final String sid;

    public AuthenticatedUser(Long userId, String sid) {
        this.userId = userId;
        this.sid = sid;
    }
}
