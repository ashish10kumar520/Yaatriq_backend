package com.yaatriq.backend.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AppUserRepository users;
    private final SecurityContextRepository securityContextRepository;
    private final String frontendUrl;

    public OAuthLoginSuccessHandler(
            AppUserRepository users,
            SecurityContextRepository securityContextRepository,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.users = users;
        this.securityContextRepository = securityContextRepository;
        this.frontendUrl = frontendUrl;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
            throw new OAuth2AuthenticationException("Unsupported OAuth login");
        }
        String provider = oauth.getAuthorizedClientRegistrationId();
        OAuth2User oauthUser = oauth.getPrincipal();
        Map<String, Object> attributes = oauthUser.getAttributes();
        String providerId = stringValue(attributes.get("sub"));
        String email = stringValue(attributes.get("email"));
        String name = stringValue(attributes.get("name"));
        boolean verifiedEmail = Boolean.TRUE.equals(attributes.get("email_verified"));

        if (providerId == null) {
            throw new OAuth2AuthenticationException("The identity provider did not return a user identifier");
        }

        AppUser user = users.findByProviderAndProviderId(provider, providerId).orElse(null);
        if (user == null && email != null) {
            user = users.findByEmailIgnoreCase(email).orElse(null);
            if (user != null) {
                if (!verifiedEmail || (user.getProvider() != null && !user.getProvider().equals(provider))) {
                    throw new OAuth2AuthenticationException(
                            "An account already exists for this email and cannot be linked to this provider."
                    );
                }
                user.linkProvider(provider, providerId);
                user = users.save(user);
            }
        }
        if (user == null) {
            user = new AppUser(
                    name == null || name.isBlank() ? provider + " traveler" : name,
                    email == null || email.isBlank() ? null : AuthService.normalizeEmail(email),
                    null
            );
            user.linkProvider(provider, providerId);
            user = users.save(user);
        }

        AppPrincipal principal = AppPrincipal.from(user);
        Authentication appAuthentication =
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()
                );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(appAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl).build().toUriString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
