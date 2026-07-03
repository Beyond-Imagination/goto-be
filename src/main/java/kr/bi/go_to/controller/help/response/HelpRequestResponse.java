package kr.bi.go_to.controller.help.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HelpRequestResponse(
        UUID id,
        String status,
        Long placeId,
        String placeName,
        String locationLabel,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer floorLevel,
        String message,
        String requesterNickname,
        String helperNickname,
        Instant requestedAt,
        Instant expiresAt,
        Instant acceptedAt,
        Instant completedAt,
        Instant canceledAt,
        String shareMessage,
        boolean emergencyCallRecommended) {}
