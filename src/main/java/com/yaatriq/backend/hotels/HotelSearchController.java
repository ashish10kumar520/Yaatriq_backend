package com.yaatriq.backend.hotels;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/hotels")
public class HotelSearchController {
    private final HotelSearchService hotelSearchService;

    public HotelSearchController(HotelSearchService hotelSearchService) {
        this.hotelSearchService = hotelSearchService;
    }

    @GetMapping("/search-links")
    public HotelSearchLinksResponse searchLinks(
            @RequestParam @NotBlank @Size(max = 200) String destination
    ) {
        return hotelSearchService.searchLinks(destination);
    }
}
