package com.yaatriq.backend.config;

import com.yaatriq.backend.auth.AppUserDetailsService;
import com.yaatriq.backend.auth.DatabaseOAuth2AuthorizationRequestRepository;
import com.yaatriq.backend.auth.OAuthLoginFailureHandler;
import com.yaatriq.backend.auth.OAuthLoginSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(
            AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:${spring.security.oauth2.client.registration.google.client-id:}}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:${spring.security.oauth2.client.registration.google.client-secret:}}") String googleClientSecret,
            @Value("${app.google-redirect-uri}") String googleRedirectUri
    ) {
        if (!googleClientId.isBlank() && !googleClientSecret.isBlank()) {
            ClientRegistration google = ClientRegistration.withRegistrationId("google")
                    .clientId(googleClientId)
                    .clientSecret(googleClientSecret)
                    .clientName("Google")
                    .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(googleRedirectUri)
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .userNameAttributeName("sub")
                    .build();
            return registrationId -> "google".equals(registrationId) ? google : null;
        }
        return registrationId -> null;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            DatabaseOAuth2AuthorizationRequestRepository authorizationRequestRepository,
            OAuthLoginSuccessHandler oauthLoginSuccessHandler,
            OAuthLoginFailureHandler oauthLoginFailureHandler
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new YaatriqCsrfTokenRequestHandler())
                        // Logout only invalidates the caller's session and must work when the CSRF cookie has expired.
                        .ignoringRequestMatchers("/api/auth/logout")
                )
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/hotels/search-links").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**", "/login/**", "/error").permitAll() // Added /login/** and /error
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"CSRF validation failed. Fetch /api/auth/csrf after login, then send its token in the returned headerName and include cookies.\"}"
                    );
                }))
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestRepository(authorizationRequestRepository)
                        )
                        .failureHandler(oauthLoginFailureHandler)
                        .successHandler(oauthLoginSuccessHandler)
                );
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        origins.add("http://localhost:[*]");
        origins.add("http://127.0.0.1:[*]");
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
