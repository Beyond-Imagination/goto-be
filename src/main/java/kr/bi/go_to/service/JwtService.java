package kr.bi.go_to.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.bi.go_to.config.security.JwtClaims;
import kr.bi.go_to.enums.TokenType;
import kr.bi.go_to.properties.JwtProperties;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JwtProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JwtService(JwtProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String createAccessToken(String subject) {
        return createToken(subject, UUID.randomUUID(), TokenType.ACCESS, properties.getAccessTokenTtl());
    }

    public String createRefreshToken(String subject, UUID tokenId) {
        return createToken(subject, tokenId, TokenType.REFRESH, properties.getRefreshTokenTtl());
    }

    public Optional<JwtClaims> parseAndValidate(String token, TokenType expectedType) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            byte[] actualSignature = decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return Optional.empty();
            }

            Map<String, Object> claims = objectMapper.readValue(decode(parts[1]), MAP_TYPE);
            String issuer = asString(claims.get("iss"));
            String subject = asString(claims.get("sub"));
            String tokenType = asString(claims.get("typ"));
            String tokenId = asString(claims.get("jti"));
            long issuedAt = asLong(claims.get("iat"));
            long expiresAt = asLong(claims.get("exp"));

            if (!properties.getIssuer().equals(issuer)
                    || subject == null
                    || tokenId == null
                    || !expectedType.name().equals(tokenType)
                    || Instant.ofEpochSecond(expiresAt).isBefore(Instant.now(clock))) {
                return Optional.empty();
            }

            return Optional.of(new JwtClaims(
                    subject,
                    UUID.fromString(tokenId),
                    TokenType.valueOf(tokenType),
                    Instant.ofEpochSecond(issuedAt),
                    Instant.ofEpochSecond(expiresAt)));
        } catch (RuntimeException | IOException ex) {
            return Optional.empty();
        }
    }

    public long accessTokenExpiresInSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    public Instant refreshTokenExpiresAt() {
        return Instant.now(clock).plus(properties.getRefreshTokenTtl());
    }

    private String createToken(String subject, UUID tokenId, TokenType tokenType, Duration ttl) {
        Instant now = Instant.now(clock);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", properties.getIssuer());
        payload.put("sub", subject);
        payload.put("jti", tokenId.toString());
        payload.put("typ", tokenType.name());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(ttl).getEpochSecond());

        String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
        return unsignedToken + "." + encode(sign(unsignedToken));
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return encode(objectMapper.writeValueAsBytes(value));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write JWT JSON", ex);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String asString(Object value) {
        return value instanceof String string ? string : null;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("JWT claim is not numeric");
    }
}
