package com.yaatriq.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthDtosTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerRequestAcceptsDisplayName() throws Exception {
        AuthDtos.RegisterRequest request = objectMapper.readValue(
                """
                        {"email":"slayerashish@gmail.com","password":"behera@166","displayName":"Ashish Kumar Behera"}
                        """,
                AuthDtos.RegisterRequest.class
        );

        assertEquals("Ashish Kumar Behera", request.name());
    }

    @Test
    void registerRequestContinuesToAcceptName() throws Exception {
        AuthDtos.RegisterRequest request = objectMapper.readValue(
                """
                        {"email":"traveler@example.com","password":"safe-password","name":"Traveler"}
                        """,
                AuthDtos.RegisterRequest.class
        );

        assertEquals("Traveler", request.name());
    }
}
