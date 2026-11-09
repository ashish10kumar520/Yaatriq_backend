package com.yaatriq.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AppUser {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 320, unique = true)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(length = 30)
    private String provider;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String name, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void linkProvider(String provider, String providerId) {
        this.provider = provider;
        this.providerId = providerId;
    }
}
