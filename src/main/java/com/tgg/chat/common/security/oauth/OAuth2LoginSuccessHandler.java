package com.tgg.chat.common.security.oauth;

import com.tgg.chat.common.security.jwt.JwtUtils;
import com.tgg.chat.common.security.token.RedisTokenStore;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.enums.AuthProvider;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.domain.user.service.UserTagGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RedisTokenStore redisTokenStore;
    private final UserTagGenerator userTagGenerator;

    @Value("${frontend_url}")
    private String frontendUrl;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        String providerId;
        String username;
        AuthProvider authProvider;
        if ("google".equals(registrationId)) {
            providerId = oauth2User.getAttribute("sub");
            username = oauth2User.getAttribute("name");
            authProvider = AuthProvider.GOOGLE;
        } else if ("kakao".equals(registrationId)) {
            Map<String, Object> attributes = oauth2User.getAttributes();
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

            providerId = String.valueOf(attributes.get("id"));
            username = (String) profile.get("nickname");
            authProvider = AuthProvider.KAKAO;
        } else {
            throw new IllegalArgumentException(
                    "지원하지 않는 OAuth Provider: " + registrationId
            );
        }

        Optional<User> optionalUser = userRepository.findByAuthProviderAndProviderId(authProvider, providerId);

        User user;

        if (optionalUser.isPresent()) {
            User findUser = optionalUser.get();

            // 탈퇴한 소셜 계정이면 로그인 차단
            if (findUser.getDeleted()) {
                response.sendRedirect(
                        frontendUrl + "/index.html?oauthError=deleted_account"
                );
                return;
            }

            user = findUser;
        } else {
            String userTag;
            do {
                userTag = userTagGenerator.generate();
            } while(userRepository.existsByUserTag(userTag));

            user = User.of(
                    null,
                    null,
                    username,
                    userTag,
                    providerId,
                    authProvider
            );

            user = userRepository.save(user);
        }

        String sid = jwtUtils.generateSid();
        String refreshToken = jwtUtils.createRefreshToken(user, sid);
        String mediaToken = jwtUtils.createMediaToken(user, sid);

        redisTokenStore.saveRefreshToken(
                user.getUserId(),
                sid,
                refreshToken,
                jwtUtils.getRefreshTokenTtlMillis()
        );

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(
                        jwtUtils.getRefreshTokenTtlMillis()
                ))
                .build();

        ResponseCookie mediaTokenCookie = ResponseCookie.from("mediaToken", mediaToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(
                        jwtUtils.getMediaTokenTtlMillis()
                ))
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookie.toString()
        );

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                mediaTokenCookie.toString()
        );

        response.sendRedirect(
                frontendUrl + "/chat.html"
        );
    }
}
