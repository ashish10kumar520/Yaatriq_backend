package com.yaatriq.backend.travel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, UUID> {
    List<TravelPlan> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<TravelPlan> findByIdAndUserId(UUID id, UUID userId);
}
