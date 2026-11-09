package com.yaatriq.backend.hotels;

import java.util.List;

public record HotelSearchLinksResponse(String destination, List<HotelSearchLink> links) {
}
