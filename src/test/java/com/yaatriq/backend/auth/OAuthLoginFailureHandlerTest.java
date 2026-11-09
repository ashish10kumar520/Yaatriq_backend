package com.yaatriq.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OAuthLoginFailureHandlerTest {
    @Test
    void redirectsToFrontendLoginWithSafeFailureCode() throws Exception {
        OAuthLoginFailureHandler handler = new OAuthLoginFailureHandler("https://yaatriq.ai");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("authorization_request_not_found"))
        );

        verify(response).sendRedirect("https://yaatriq.ai/login?error=oauth_failed");
    }
}
