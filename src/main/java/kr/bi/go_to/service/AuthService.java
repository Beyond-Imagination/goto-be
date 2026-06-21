package kr.bi.go_to.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.controller.auth.response.AccessTokenResponse;
import kr.bi.go_to.controller.auth.request.LoginRequest;
import kr.bi.go_to.controller.auth.response.LoginResponse;
import kr.bi.go_to.controller.auth.request.RefreshRequest;
import kr.bi.go_to.model.refreshToken.RefreshToken;
import kr.bi.go_to.model.refreshToken.RefreshTokenRepository;
import kr.bi.go_to.config.security.JwtClaims;
import kr.bi.go_to.enums.TokenType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        UUID refreshTokenId = UUID.randomUUID();

        refreshTokenRepository.save(new RefreshToken(
                refreshTokenId,
                username,
                jwtService.refreshTokenExpiresAt()
        ));

        return new LoginResponse(
                jwtService.createAccessToken(username),
                jwtService.createRefreshToken(username, refreshTokenId),
                "Bearer",
                jwtService.accessTokenExpiresInSeconds()
        );
    }

    @Transactional(readOnly = true)
    public AccessTokenResponse refresh(RefreshRequest request) {
        JwtClaims claims = jwtService.parseAndValidate(request.refreshToken(), TokenType.REFRESH)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        RefreshToken refreshToken = refreshTokenRepository.findById(claims.tokenId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown refresh token"));

        if (refreshToken.isRevoked()
                || refreshToken.getExpiresAt().isBefore(Instant.now(clock))
                || !refreshToken.getUsername().equals(claims.subject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired or revoked refresh token");
        }

        return new AccessTokenResponse(
                jwtService.createAccessToken(claims.subject()),
                "Bearer",
                jwtService.accessTokenExpiresInSeconds()
        );
    }
}
