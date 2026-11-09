package com.yaatriq.backend.auth;

import com.yaatriq.backend.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuthService authService = new AuthService(users, passwordEncoder);

    @Test
    void registerNormalizesEmailAndStoresOnlyPasswordHash() {
        when(users.existsByEmailIgnoreCase("traveler@example.com")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthDtos.UserResponse result = authService.register(
                new AuthDtos.RegisterRequest(" Traveler ", "Traveler@Example.com", "safe-password")
        );

        assertEquals("traveler@example.com", result.email());
        assertEquals("Traveler", result.name());
        org.mockito.ArgumentCaptor<AppUser> userCaptor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();
        assertNotEquals("safe-password", savedUser.getPasswordHash());
        assertEquals(true, passwordEncoder.matches("safe-password", savedUser.getPasswordHash()));
    }

    @Test
    void registerRejectsAnExistingEmail() {
        when(users.existsByEmailIgnoreCase("traveler@example.com")).thenReturn(true);

        assertThrows(ApiException.class, () -> authService.register(
                new AuthDtos.RegisterRequest("Traveler", "traveler@example.com", "safe-password")
        ));
        verify(users, never()).save(any(AppUser.class));
    }
}
