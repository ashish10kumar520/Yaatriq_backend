package com.yaatriq.backend.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaatriq.backend.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpStatus;

class TravelPlanServiceTest {
    @Test
    void generationFailsExplicitlyWhenGroqIsNotConfigured() {
        TravelPlanService service = new TravelPlanService(
                mock(TravelPlanRepository.class),
                RestClient.builder(),
                new ObjectMapper(),
                "",
                "https://api.groq.com/openai/v1",
                "test-model"
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.generate(
                UUID.randomUUID(),
                new TravelPlanDtos.GenerateRequest("7 days in Vietnam", "Vietnam", 7, null, null, null)
        ));

        assertEquals(503, exception.getStatus().value());
        assertEquals(
                "Travel-plan generation is unavailable until GROQ_API_KEY is configured",
                exception.getMessage()
        );
    }

    @Test
    void generationCallsGroqChatCompletionsEndpointAndPersistsItsResponse() {
        TravelPlanRepository plans = mock(TravelPlanRepository.class);
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        when(plans.save(any(TravelPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-grok-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "model": "test-model",
                          "response_format": {"type": "json_object"}
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {
                              "content": "{\\"summary\\":\\"A week in Bali\\",\\"days\\":[]}"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        TravelPlanService service = new TravelPlanService(
                plans,
                restClientBuilder,
                new ObjectMapper(),
                "test-grok-api-key",
                "https://api.groq.com/openai/v1",
                "test-model"
        );

        TravelPlanDtos.PlanResponse result = service.generate(
                UUID.randomUUID(),
                new TravelPlanDtos.GenerateRequest("A week in Bali", "Bali", 7, null, null, "Balanced")
        );

        assertEquals("A week in Bali", result.itinerary().get("summary").asText());
        server.verify();
    }

    @Test
    void returnsUsefulErrorWhenConfiguredGroqModelIsNotFound() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(content().json("{\"model\":\"removed-model\"}", false))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        TravelPlanService service = new TravelPlanService(
                mock(TravelPlanRepository.class),
                restClientBuilder,
                new ObjectMapper(),
                "test-grok-api-key",
                "https://api.groq.com/openai/v1",
                "removed-model"
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.generate(
                UUID.randomUUID(),
                new TravelPlanDtos.GenerateRequest("A week in Bali", "Bali", 7, null, null, null)
        ));

        assertEquals(502, exception.getStatus().value());
        assertEquals(
                "Groq model 'removed-model' was not found. Set GROQ_MODEL to a model available to your Groq API key.",
                exception.getMessage()
        );
        server.verify();
    }

    @Test
    void retriesTransientGroqServerErrorAndReturnsSuccessfulItinerary() {
        TravelPlanRepository plans = mock(TravelPlanRepository.class);
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        when(plans.save(any(TravelPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String endpoint = "https://api.groq.com/openai/v1/chat/completions";
        server.expect(requestTo(endpoint))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(endpoint))
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {
                              "content": "{\\"summary\\":\\"Recovered itinerary\\",\\"days\\":[]}"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        TravelPlanService service = new TravelPlanService(
                plans,
                restClientBuilder,
                new ObjectMapper(),
                "test-groq-api-key",
                "https://api.groq.com/openai/v1",
                "test-model"
        );

        TravelPlanDtos.PlanResponse result = service.generate(
                UUID.randomUUID(),
                new TravelPlanDtos.GenerateRequest("A week in Bali", "Bali", 7, null, null, null)
        );

        assertEquals("Recovered itinerary", result.itinerary().get("summary").asText());
        server.verify();
    }

    @Test
    void stopsAfterMaximumGroqServerErrorAttempts() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        String endpoint = "https://api.groq.com/openai/v1/chat/completions";
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(requestTo(endpoint))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        }

        TravelPlanService service = new TravelPlanService(
                mock(TravelPlanRepository.class),
                restClientBuilder,
                new ObjectMapper(),
                "test-groq-api-key",
                "https://api.groq.com/openai/v1",
                "test-model"
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.generate(
                UUID.randomUUID(),
                new TravelPlanDtos.GenerateRequest("A week in Bali", "Bali", 7, null, null, null)
        ));

        assertEquals(502, exception.getStatus().value());
        server.verify();
    }
}
