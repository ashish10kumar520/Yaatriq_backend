package com.yaatriq.backend.travel;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "travel_plans")
public class TravelPlan {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(length = 160)
    private String destination;

    private Integer days;

    @Column(precision = 12, scale = 2)
    private BigDecimal budget;

    @Column(length = 3)
    private String currency;

    @Column(name = "travel_style", length = 80)
    private String travelStyle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode itinerary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TravelPlan() {
    }

    public TravelPlan(
            UUID userId,
            String prompt,
            String destination,
            Integer days,
            BigDecimal budget,
            String currency,
            String travelStyle,
            JsonNode itinerary
    ) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.prompt = prompt;
        this.destination = destination;
        this.days = days;
        this.budget = budget;
        this.currency = currency;
        this.travelStyle = travelStyle;
        this.itinerary = itinerary;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getDestination() {
        return destination;
    }

    public Integer getDays() {
        return days;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTravelStyle() {
        return travelStyle;
    }

    public JsonNode getItinerary() {
        return itinerary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
