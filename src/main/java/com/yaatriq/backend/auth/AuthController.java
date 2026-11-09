package com.yaatriq.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName()
        );
    }

    @PostMapping("/register")
    public AuthDtos.UserResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.UserResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        AuthService.normalizeEmail(request.email()),
                        request.password()
                )
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return AuthDtos.UserResponse.from(authService.findByEmail(authentication.getName()));
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return AuthDtos.UserResponse.from(authService.findById(principal.getId()));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        logoutHandler.logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
        return Map.of("message", "Logged out");
    }
}
