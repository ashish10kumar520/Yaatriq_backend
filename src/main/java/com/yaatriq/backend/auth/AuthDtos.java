package com.yaatriq.backend.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @JsonAlias("displayName") @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank String password
    ) {
    }

    public record UserResponse(UUID id, String name, String email, Instant createdAt) {
        static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
        }
    }
}
