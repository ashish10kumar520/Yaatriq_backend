package com.yaatriq.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {
    @Test
    void googleRegistrationUsesTheConfiguredRedirectUri() {
        String redirectUri = "http://localhost:8080/login/oauth2/code/google";
        ClientRegistrationRepository registrations = new SecurityConfig()
                .clientRegistrationRepository("google-client", "google-secret", redirectUri);

        ClientRegistration google = registrations.findByRegistrationId("google");

        assertEquals(redirectUri, google.getRedirectUri());
        assertEquals("https://www.googleapis.com/oauth2/v3/certs", google.getProviderDetails().getJwkSetUri());
    }

    @Test
    void googleRegistrationIsNotAvailableWithoutBothCredentials() {
        ClientRegistrationRepository registrations = new SecurityConfig()
                .clientRegistrationRepository("google-client", "", "http://localhost:8080/login/oauth2/code/google");

        org.junit.jupiter.api.Assertions.assertNull(registrations.findByRegistrationId("google"));
    }

    @Test
    void corsAllowsProductionFrontendWithCredentialedRequests() {
        CorsConfigurationSource source = new SecurityConfig()
                .corsConfigurationSource("https://yaatriq.ai, https://www.yaatriq.ai");
        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("OPTIONS", "/api/auth/logout")
        );

        assertEquals("https://yaatriq.ai", configuration.checkOrigin("https://yaatriq.ai"));
        assertEquals("https://www.yaatriq.ai", configuration.checkOrigin("https://www.yaatriq.ai"));
        assertEquals("http://localhost:5173", configuration.checkOrigin("http://localhost:5173"));
        assertEquals("http://127.0.0.1:3000", configuration.checkOrigin("http://127.0.0.1:3000"));
        assertTrue(configuration.getAllowCredentials());
        assertTrue(configuration.getAllowedHeaders().contains("*"));
        assertTrue(configuration.getAllowedMethods().contains("OPTIONS"));
        assertTrue(configuration.getAllowedMethods().contains("POST"));
    }

    @Test
    void csrfHandlerAcceptsBothCommonHeaderNames() {
        YaatriqCsrfTokenRequestHandler handler = new YaatriqCsrfTokenRequestHandler();
        DefaultCsrfToken csrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest();

        request.addHeader("X-XSRF-TOKEN", "xsrf-header-token");
        assertEquals("xsrf-header-token", handler.resolveCsrfTokenValue(request, csrfToken));

        request.removeHeader("X-XSRF-TOKEN");
        request.addHeader("X-CSRF-TOKEN", "csrf-header-token");
        assertEquals("csrf-header-token", handler.resolveCsrfTokenValue(request, csrfToken));

        request.removeHeader("X-CSRF-TOKEN");
        request.addHeader("X-CSRFToken", "axios-header-token");
        assertEquals("axios-header-token", handler.resolveCsrfTokenValue(request, csrfToken));
    }
}
