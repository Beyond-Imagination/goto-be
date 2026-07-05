package kr.bi.go_to.service.place.model;

import java.time.Instant;

public record PlaceData(
        long id,
        String externalId,
        String source,
        String category,
        String name,
        String sanitizedAddress,
        double latitude,
        double longitude,
        String thumbnailUrl,
        BfDetailsData bfDetails,
        Instant bfLastSyncedAt) {}
