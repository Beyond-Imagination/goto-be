package kr.bi.go_to.controller.help.response;

import java.time.Instant;
import java.util.UUID;

public record NearbyHelpRequestResponse(
        UUID id,
        Long placeId,
        String placeName,
        String locationLabel,
        String message,
        long distanceMeters,
        Instant requestedAt,
        Instant expiresAt) {}
