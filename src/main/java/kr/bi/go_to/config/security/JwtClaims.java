package kr.bi.go_to.config.security;

import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.enums.TokenType;

public record JwtClaims(String subject, UUID tokenId, TokenType tokenType, Instant issuedAt, Instant expiresAt) {}
