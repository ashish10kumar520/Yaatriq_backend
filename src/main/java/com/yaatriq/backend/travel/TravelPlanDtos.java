package com.yaatriq.backend.travel;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TravelPlanDtos {
    private TravelPlanDtos() {
    }

    public record GenerateRequest(
            @NotBlank @Size(max = 4000) String prompt,
            @Size(max = 160) String destination,
            @Min(1) @Max(60) Integer days,
            @DecimalMin("0.0") BigDecimal budget,
            @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Size(max = 80) String travelStyle
    ) {
    }

    public record PlanResponse(
            UUID id,
            String prompt,
            String destination,
            Integer days,
            BigDecimal budget,
            String currency,
            String travelStyle,
            JsonNode itinerary,
            Instant createdAt,
            Instant updatedAt
    ) {
        static PlanResponse from(TravelPlan plan) {
            return new PlanResponse(
                    plan.getId(),
                    plan.getPrompt(),
                    plan.getDestination(),
                    plan.getDays(),
                    plan.getBudget(),
                    plan.getCurrency(),
                    plan.getTravelStyle(),
                    plan.getItinerary(),
                    plan.getCreatedAt(),
                    plan.getUpdatedAt()
            );
        }
    }
}
