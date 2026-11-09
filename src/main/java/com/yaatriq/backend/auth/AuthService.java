package com.yaatriq.backend.auth;

import com.yaatriq.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password())
        ));
        return AuthDtos.UserResponse.from(user);
    }

    public AppUser findByEmail(String email) {
        return users.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
    }

    public AppUser findById(java.util.UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
