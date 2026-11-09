package com.yaatriq.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class DatabaseOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private static final Logger logger =
            LoggerFactory.getLogger(DatabaseOAuth2AuthorizationRequestRepository.class);
    private static final int REQUEST_TTL_MINUTES = 10;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DatabaseOAuth2AuthorizationRequestRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null || state.isBlank()) {
            return null;
        }

        String json = jdbcTemplate.query(
                """
                SELECT request_data
                FROM oauth2_authorization_requests
                WHERE state = ? AND expires_at > CURRENT_TIMESTAMP
                """,
                resultSet -> resultSet.next() ? resultSet.getString("request_data") : null,
                state
        );

        if (json == null) {
            // Do NOT log as WARN here if this is a second call after a successful remove.
            // Logging at DEBUG is safer.
            logger.debug("OAuth authorization request not found or expired: stateFingerprint={}", fingerprint(state));
            return null;
        }

        logger.info("Loaded OAuth authorization request: stateFingerprint={}", fingerprint(state));
        return deserialize(json);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            deleteRequest(request.getParameter("state"));
            return;
        }

        jdbcTemplate.update("DELETE FROM oauth2_authorization_requests WHERE expires_at <= CURRENT_TIMESTAMP");
        jdbcTemplate.update(
                """
                INSERT INTO oauth2_authorization_requests (state, request_data, expires_at)
                VALUES (?, ?::jsonb, ?)
                ON CONFLICT (state) DO UPDATE
                SET request_data = EXCLUDED.request_data, expires_at = EXCLUDED.expires_at
                """,
                authorizationRequest.getState(),
                serialize(StoredAuthorizationRequest.from(authorizationRequest)),
                Timestamp.from(Instant.now().plusSeconds(REQUEST_TTL_MINUTES * 60L))
        );
        logger.info(
                "Stored OAuth authorization request: stateFingerprint={}, expiresInMinutes={}",
                fingerprint(authorizationRequest.getState()),
                REQUEST_TTL_MINUTES
        );
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String state = request.getParameter("state");
        if (state == null || state.isBlank()) {
            return null;
        }

        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        // Always try to delete – even if load returned null
        deleteRequest(state);
        return authorizationRequest;
    }

    private void deleteRequest(String state) {
        if (state != null && !state.isBlank()) {
            jdbcTemplate.update("DELETE FROM oauth2_authorization_requests WHERE state = ?", state);
            logger.debug("Removed OAuth authorization request: stateFingerprint={}", fingerprint(state));
        }
    }

    private String fingerprint(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String serialize(StoredAuthorizationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the OAuth authorization request", exception);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String json) {
        try {
            StoredAuthorizationRequest stored = objectMapper.readValue(json, StoredAuthorizationRequest.class);
            OAuth2AuthorizationRequest request = stored.toAuthorizationRequest();
            logger.debug("Deserialized attributes keys: {}", request.getAttributes().keySet());
            return request;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize the OAuth authorization request", exception);
        }
    }

    private record StoredAuthorizationRequest(
            String authorizationUri,
            String authorizationRequestUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes
    ) {
        static StoredAuthorizationRequest from(OAuth2AuthorizationRequest request) {
            return new StoredAuthorizationRequest(
                    request.getAuthorizationUri(),
                    request.getAuthorizationRequestUri(),
                    request.getClientId(),
                    request.getRedirectUri(),
                    request.getScopes(),
                    request.getState(),
                    request.getAdditionalParameters(),
                    request.getAttributes()
            );
        }

        OAuth2AuthorizationRequest toAuthorizationRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(authorizationUri)
                    .authorizationRequestUri(authorizationRequestUri)
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scopes(scopes)
                    .state(state)
                    .additionalParameters(additionalParameters == null ? Map.of() : additionalParameters)
                    .attributes(attributes == null ? Map.of() : attributes)
                    .build();
        }
    }
}
