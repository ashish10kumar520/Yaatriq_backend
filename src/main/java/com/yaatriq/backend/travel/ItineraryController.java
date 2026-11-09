package com.yaatriq.backend.travel;

import com.yaatriq.backend.auth.AppPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/itineraries")
public class ItineraryController {
    private final TravelPlanService travelPlanService;

    public ItineraryController(TravelPlanService travelPlanService) {
        this.travelPlanService = travelPlanService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelPlanDtos.PlanResponse generate(
            Authentication authentication,
            @Valid @RequestBody TravelPlanDtos.GenerateRequest request
    ) {
        AppPrincipal principal = (AppPrincipal) authentication.getPrincipal();
        return travelPlanService.generate(principal.getId(), request);
    }
}
