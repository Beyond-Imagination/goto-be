package kr.bi.go_to.config.security;

import kr.bi.go_to.enums.TokenType;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(
        String subject,
        UUID tokenId,
        TokenType tokenType,
        Instant issuedAt,
        Instant expiresAt
) {
}
