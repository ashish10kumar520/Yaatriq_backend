package com.yaatriq.backend.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AppPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String passwordHash;

    public AppPrincipal(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static AppPrincipal from(AppUser user) {
        return new AppPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
    }

    public UUID getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email == null ? id.toString() : email;
    }
}
