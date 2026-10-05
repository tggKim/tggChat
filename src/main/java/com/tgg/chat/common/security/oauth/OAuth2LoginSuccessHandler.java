package com.tgg.chat.common.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        Map<String, Object> attributes =
                oauth2User.getAttributes();

        log.info("OAuth2 로그인 성공");
        log.info("attributes = {}", attributes);

        log.info("providerId = {}", attributes.get("sub"));
        log.info("email = {}", attributes.get("email"));
        log.info("name = {}", attributes.get("name"));
        log.info("picture = {}", attributes.get("picture"));

        response.setStatus(HttpServletResponse.SC_OK);
    }
}
