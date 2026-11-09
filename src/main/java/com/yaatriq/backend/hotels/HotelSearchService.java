package com.yaatriq.backend.hotels;

import com.yaatriq.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HotelSearchService {
    private static final Map<String, String> DESTINATION_CANONICAL_NAMES = Map.of(
            "hanoi", "Hanoi, Vietnam",
            "hanoi, vietnam", "Hanoi, Vietnam"
    );

    public HotelSearchLinksResponse searchLinks(String destination) {
        String normalizedDestination = normalizeDestination(destination);
        String canonicalDestination = DESTINATION_CANONICAL_NAMES.getOrDefault(
                normalizedDestination.toLowerCase(Locale.ROOT),
                normalizedDestination
        );
        String bookingUrl = UriComponentsBuilder.fromUriString("https://www.booking.com/searchresults.html")
                .queryParam("ss", canonicalDestination)
                .build()
                .encode()
                .toUriString();
        String agodaUrl = UriComponentsBuilder.fromUriString("https://www.agoda.com/search")
                .queryParam("text", canonicalDestination)
                .build()
                .encode()
                .toUriString();

        return new HotelSearchLinksResponse(canonicalDestination, List.of(
                new HotelSearchLink("Booking.com", bookingUrl),
                new HotelSearchLink("Airbnb", airbnbUrl(canonicalDestination)),
                new HotelSearchLink("Agoda", agodaUrl)
        ));
    }

    private String normalizeDestination(String destination) {
        String normalized = destination == null ? "" : destination.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "destination must not be blank");
        }
        return normalized;
    }

    private String airbnbUrl(String destination) {
        String[] parts = destination.split(",", 2);
        String city = slug(parts[0]);
        String location = parts.length == 2 ? city + "--" + slug(parts[1]) : city;
        return "https://www.airbnb.com/s/"
                + UriUtils.encodePathSegment(location, StandardCharsets.UTF_8)
                + "/homes";
    }

    private String slug(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }
}
