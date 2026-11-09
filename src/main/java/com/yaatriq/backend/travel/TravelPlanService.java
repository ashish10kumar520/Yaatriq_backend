package com.yaatriq.backend.travel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaatriq.backend.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TravelPlanService {
    private static final Logger logger = LoggerFactory.getLogger(TravelPlanService.class);
    private static final int MAX_PROVIDER_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MILLIS = 200;

    private final TravelPlanRepository plans;
    private final RestClient groqClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public TravelPlanService(
            TravelPlanRepository plans,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.groq.api-key}") String apiKey,
            @Value("${app.groq.base-url}") String baseUrl,
            @Value("${app.groq.model}") String model
    ) {
        this.plans = plans;
        this.groqClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Transactional
    public TravelPlanDtos.PlanResponse generate(UUID userId, TravelPlanDtos.GenerateRequest request) {
        if (apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Travel-plan generation is unavailable until GROQ_API_KEY is configured"
            );
        }

        JsonNode itinerary = generateItinerary(request);
        TravelPlan plan = plans.save(new TravelPlan(
                userId,
                request.prompt().trim(),
                clean(request.destination()),
                request.days(),
                request.budget(),
                request.currency() == null ? null : request.currency().toUpperCase(),
                clean(request.travelStyle()),
                itinerary
        ));
        return TravelPlanDtos.PlanResponse.from(plan);
    }

    @Transactional(readOnly = true)
    public List<TravelPlanDtos.PlanResponse> list(UUID userId) {
        return plans.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(TravelPlanDtos.PlanResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TravelPlanDtos.PlanResponse get(UUID userId, UUID planId) {
        return plans.findByIdAndUserId(planId, userId)
                .map(TravelPlanDtos.PlanResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Travel plan not found"));
    }

    @Transactional
    public void delete(UUID userId, UUID planId) {
        TravelPlan plan = plans.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Travel plan not found"));
        plans.delete(plan);
    }

    private JsonNode generateItinerary(TravelPlanDtos.GenerateRequest request) {
        String systemPrompt = """
                Create a practical, budget-aware travel itinerary. Return only a valid JSON object with these keys:
                summary (string), estimated_total (number or null), currency (3-letter string or null),
                budget_notes (string), days (array of objects with day (number), title (string),
                activities (array of strings), meals (array of strings), estimated_cost (number or null)).
                Keep the plan consistent with the user's requested destination, duration, budget, travel style and prompt.
                Do not invent exact live prices; label estimates as approximate in budget_notes.
                """;
        String userPrompt = """
                User request: %s
                Destination: %s
                Duration in days: %s
                Budget: %s %s
                Travel style: %s
                """.formatted(
                request.prompt(),
                valueOrUnknown(request.destination()),
                valueOrUnknown(request.days()),
                valueOrUnknown(request.budget()),
                valueOrUnknown(request.currency()),
                valueOrUnknown(request.travelStyle())
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String content = callProviderWithRetry(body);
        try {
            JsonNode itinerary = objectMapper.readTree(content);
            if (!itinerary.isObject()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "The Groq provider returned an invalid itinerary");
            }
            return itinerary;
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The Groq provider returned malformed itinerary data");
        }
    }

    private String callProviderWithRetry(Map<String, Object> body) {
        long retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
        for (int attempt = 1; attempt <= MAX_PROVIDER_ATTEMPTS; attempt++) {
            try {
                JsonNode response = groqClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode contentNode = response == null ? null : response.at("/choices/0/message/content");
                if (contentNode == null || !contentNode.isTextual()) {
                    throw new ApiException(
                            HttpStatus.BAD_GATEWAY,
                            "The Groq provider returned an invalid response"
                    );
                }
                return contentNode.asText();
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().is5xxServerError() && attempt < MAX_PROVIDER_ATTEMPTS) {
                    logger.warn("Groq itinerary request returned HTTP {}; retrying ({}/{})",
                            exception.getStatusCode().value(), attempt, MAX_PROVIDER_ATTEMPTS);
                    waitBeforeRetry(retryDelayMillis);
                    retryDelayMillis *= 2;
                    continue;
                }
                throw providerError(exception);
            } catch (RestClientException exception) {
                if (attempt < MAX_PROVIDER_ATTEMPTS) {
                    logger.warn("Groq itinerary request failed: {}; retrying ({}/{})",
                            exception.getClass().getSimpleName(), attempt, MAX_PROVIDER_ATTEMPTS);
                    waitBeforeRetry(retryDelayMillis);
                    retryDelayMillis *= 2;
                    continue;
                }
                logger.warn("Groq itinerary request failed after {} attempts: {}",
                        MAX_PROVIDER_ATTEMPTS, exception.getClass().getSimpleName());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "The Groq provider could not generate a plan");
            }
        }
        throw new IllegalStateException("Groq retry loop ended without a response");
    }

    private void waitBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Groq itinerary generation was interrupted during retry");
        }
    }

    private ApiException providerError(RestClientResponseException exception) {
        logger.warn("Groq itinerary request returned HTTP {}", exception.getStatusCode().value());
        String message = exception.getStatusCode().value() == 404
                ? "Groq model '" + model + "' was not found. Set GROQ_MODEL to a model available to your Groq API key."
                : "The Groq API rejected the itinerary request (HTTP "
                        + exception.getStatusCode().value() + ")";
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                message
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "not specified" : value.toString();
    }
}
