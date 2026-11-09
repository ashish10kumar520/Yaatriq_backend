package com.yaatriq.backend.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {
    private static final Logger logger = LoggerFactory.getLogger(OAuthLoginFailureHandler.class);

    private final String frontendUrl;

    public OAuthLoginFailureHandler(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String errorCode = exception instanceof OAuth2AuthenticationException oauthException
                ? oauthException.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        logger.warn("OAuth sign-in failed: errorCode={}, exceptionType={}", errorCode, exception.getClass().getSimpleName());

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/login")
                .queryParam("error", "oauth_failed")
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
