package com.tgg.chat.common.security.jwt;

import org.springframework.stereotype.Component;

import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccessTokenAuthenticator {

    private final JwtUtils jwtUtils;

    public AuthenticatedUser authenticateBearerToken(String bearerString) {
        if (bearerString == null) {
            throw new ErrorException(ErrorCode.JWT_MISSING_AUTH_HEADER);
        }

        if (!bearerString.startsWith("Bearer ")) {
            throw new ErrorException(ErrorCode.JWT_INVALID_AUTH_SCHEME);
        }

        String jwtString = bearerString.substring(7);
        Claims claims = jwtUtils.parseClaims(jwtString);

        if(!jwtUtils.isAccessToken(claims)) {
            throw new ErrorException(ErrorCode.JWT_INVALID_TOKEN);
        }

        Long userId = Long.parseLong(claims.getSubject());
        String sid = jwtUtils.getSid(claims);

        return new AuthenticatedUser(
                userId,
                sid
        );
    }
}
