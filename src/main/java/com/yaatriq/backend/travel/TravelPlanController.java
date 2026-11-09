package com.yaatriq.backend.travel;

import com.yaatriq.backend.auth.AppPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/travel-plans")
public class TravelPlanController {
    private final TravelPlanService travelPlanService;

    public TravelPlanController(TravelPlanService travelPlanService) {
        this.travelPlanService = travelPlanService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelPlanDtos.PlanResponse generate(
            Authentication authentication,
            @Valid @RequestBody TravelPlanDtos.GenerateRequest request
    ) {
        return travelPlanService.generate(principal(authentication).getId(), request);
    }

    @GetMapping
    public List<TravelPlanDtos.PlanResponse> list(Authentication authentication) {
        return travelPlanService.list(principal(authentication).getId());
    }

    @GetMapping("/{planId}")
    public TravelPlanDtos.PlanResponse get(Authentication authentication, @PathVariable UUID planId) {
        return travelPlanService.get(principal(authentication).getId(), planId);
    }

    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID planId) {
        travelPlanService.delete(principal(authentication).getId(), planId);
    }

    private AppPrincipal principal(Authentication authentication) {
        return (AppPrincipal) authentication.getPrincipal();
    }
}
