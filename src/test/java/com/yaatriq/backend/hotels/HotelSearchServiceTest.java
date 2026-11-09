package com.yaatriq.backend.hotels;

import com.yaatriq.backend.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HotelSearchServiceTest {
    private final HotelSearchService service = new HotelSearchService();

    @Test
    void createsHotelSearchLinksForCanonicalHanoiDestination() {
        HotelSearchLinksResponse response = service.searchLinks(" Hanoi ");

        assertEquals("Hanoi, Vietnam", response.destination());
        assertEquals(3, response.links().size());
        assertEquals("Booking.com", response.links().get(0).provider());
        assertEquals(
                "Hanoi, Vietnam",
                decodedQueryParameter(response.links().get(0).url(), "ss")
        );
        assertEquals("Airbnb", response.links().get(1).provider());
        assertEquals("https://www.airbnb.com/s/hanoi--vietnam/homes", response.links().get(1).url());
        assertEquals("Agoda", response.links().get(2).provider());
        assertEquals(
                "Hanoi, Vietnam",
                decodedQueryParameter(response.links().get(2).url(), "text")
        );
    }

    @Test
    void trimsAndEncodesOtherDestinations() {
        HotelSearchLinksResponse response = service.searchLinks("  New York, USA  ");

        assertEquals("New York, USA", response.destination());
        assertEquals(
                "New York, USA",
                decodedQueryParameter(response.links().get(0).url(), "ss")
        );
        assertEquals("https://www.airbnb.com/s/new-york--usa/homes", response.links().get(1).url());
    }

    @Test
    void rejectsBlankDestination() {
        ApiException exception = assertThrows(ApiException.class, () -> service.searchLinks("   "));

        assertEquals(400, exception.getStatus().value());
    }

    private String decodedQueryParameter(String url, String name) {
        return URLDecoder.decode(
                UriComponentsBuilder.fromUriString(url)
                        .build()
                        .getQueryParams()
                        .getFirst(name),
                StandardCharsets.UTF_8
        );
    }
}
