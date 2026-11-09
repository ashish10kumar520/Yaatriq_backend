package com.yaatriq.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginSuccessHandlerTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    private final OAuthLoginSuccessHandler handler =
            new OAuthLoginSuccessHandler(users, securityContextRepository, "https://yaatriq.ai");
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void googleLoginLinksExistingAccountWhenEmailIsVerified() throws Exception {
        AppUser existingUser = new AppUser("Traveler", "traveler@example.com", "password-hash");
        when(users.findByProviderAndProviderId("google", "google-subject")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("traveler@example.com")).thenReturn(Optional.of(existingUser));
        when(users.save(existingUser)).thenReturn(existingUser);

        handler.onAuthenticationSuccess(
                request,
                response,
                googleAuthentication(true)
        );

        assertEquals("google", existingUser.getProvider());
        assertEquals("google-subject", existingUser.getProviderId());
        verify(users).save(existingUser);
        verify(response).sendRedirect("https://yaatriq.ai");
    }

    @Test
    void googleLoginDoesNotLinkExistingAccountWhenEmailIsNotVerified() {
        when(users.findByProviderAndProviderId("google", "google-subject")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("traveler@example.com"))
                .thenReturn(Optional.of(new AppUser("Traveler", "traveler@example.com", "password-hash")));

        assertThrows(OAuth2AuthenticationException.class, () -> handler.onAuthenticationSuccess(
                request,
                response,
                googleAuthentication(false)
        ));

        verify(users, never()).save(any(AppUser.class));
    }

    private OAuth2AuthenticationToken googleAuthentication(boolean emailVerified) {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-subject",
                        "email", "traveler@example.com",
                        "email_verified", emailVerified,
                        "name", "Traveler"
                ),
                "sub"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
