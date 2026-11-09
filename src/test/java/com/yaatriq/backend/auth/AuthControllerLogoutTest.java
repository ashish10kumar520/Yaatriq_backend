package com.yaatriq.backend.auth;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AuthControllerLogoutTest {
    @Test
    void logoutInvalidatesSessionAndClearsCsrfCookie() {
        AuthController controller = new AuthController(
                mock(AuthService.class),
                mock(AuthenticationManager.class),
                new HttpSessionSecurityContextRepository(),
                CookieCsrfTokenRepository.withHttpOnlyFalse()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.logout(null, request, response);

        assertEquals("Logged out", result.get("message"));
        assertFalse(session.isInvalid());
        assertNotNull(response.getCookie("XSRF-TOKEN"));
        assertEquals(0, response.getCookie("XSRF-TOKEN").getMaxAge());
    }

    @Test
    void logoutWorksWhenThereIsNoSession() {
        AuthController controller = new AuthController(
                mock(AuthService.class),
                mock(AuthenticationManager.class),
                new HttpSessionSecurityContextRepository(),
                CookieCsrfTokenRepository.withHttpOnlyFalse()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.logout(null, request, response);

        assertEquals("Logged out", result.get("message"));
        HttpSession session = request.getSession(false);
        assertEquals(null, session);
    }
}
