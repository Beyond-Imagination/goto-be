package kr.bi.go_to.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.config.security.JwtClaims;
import kr.bi.go_to.controller.auth.request.OAuthLoginRequest;
import kr.bi.go_to.controller.auth.request.OAuthSignupRequest;
import kr.bi.go_to.controller.auth.request.RefreshRequest;
import kr.bi.go_to.controller.auth.response.AccessTokenResponse;
import kr.bi.go_to.controller.auth.response.OAuthAuthenticationResponse;
import kr.bi.go_to.enums.AgreementType;
import kr.bi.go_to.enums.TokenType;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.refreshToken.RefreshToken;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.OAuthUserRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.service.oauth.OAuthIdentity;
import kr.bi.go_to.service.oauth.OAuthIdentityVerifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthUserRepository oauthUserRepository;
    private final MemberRepository memberRepository;
    private final OAuthIdentityVerifier oauthIdentityVerifier;
    private final OAuthRegistrationService oauthRegistrationService;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(
            RefreshTokenRepository refreshTokenRepository,
            OAuthUserRepository oauthUserRepository,
            MemberRepository memberRepository,
            OAuthIdentityVerifier oauthIdentityVerifier,
            OAuthRegistrationService oauthRegistrationService,
            JwtService jwtService,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.oauthUserRepository = oauthUserRepository;
        this.memberRepository = memberRepository;
        this.oauthIdentityVerifier = oauthIdentityVerifier;
        this.oauthRegistrationService = oauthRegistrationService;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public OAuthAuthenticationResponse login(OAuthLoginRequest request) {
        OAuthIdentity identity = oauthIdentityVerifier.verify(request.provider(), request.providerAccessToken());
        return oauthUserRepository
                .findByProviderAndProviderId(identity.provider(), identity.providerId())
                .map(oauthUser -> authenticated(oauthUser.getMember()))
                .orElseGet(() ->
                        OAuthAuthenticationResponse.signUpRequired(identity.provider(), identity.suggestedNickname()));
    }

    @Transactional
    public OAuthAuthenticationResponse signup(OAuthSignupRequest request) {
        OAuthIdentity identity = oauthIdentityVerifier.verify(request.provider(), request.providerAccessToken());
        if (!AgreementType.hasRequiredAgreements(request.agreementMask())) {
            throw new BusinessException(ErrorCode.REQUIRED_AGREEMENTS_NOT_ACCEPTED);
        }

        String nickname = request.nickname().trim();
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_IN_USE);
        }

        return authenticated(
                oauthRegistrationService.register(identity, nickname, request.agreementMask(), request.preferences()));
    }

    private OAuthAuthenticationResponse authenticated(Member member) {
        String subject = member.getId().toString();
        UUID refreshTokenId = UUID.randomUUID();

        refreshTokenRepository.save(new RefreshToken(refreshTokenId, subject, jwtService.refreshTokenExpiresAt()));

        return OAuthAuthenticationResponse.authenticated(
                jwtService.createAccessToken(subject),
                jwtService.createRefreshToken(subject, refreshTokenId),
                jwtService.accessTokenExpiresInSeconds());
    }

    @Transactional(readOnly = true)
    public AccessTokenResponse refresh(RefreshRequest request) {
        JwtClaims claims = jwtService
                .parseAndValidate(request.refreshToken(), TokenType.REFRESH)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        RefreshToken refreshToken = refreshTokenRepository
                .findById(claims.tokenId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN_REFRESH_TOKEN));

        if (refreshToken.isRevoked()
                || refreshToken.getExpiresAt().isBefore(Instant.now(clock))
                || !refreshToken.getSubject().equals(claims.subject())) {
            throw new BusinessException(ErrorCode.EXPIRED_OR_REVOKED_REFRESH_TOKEN);
        }

        return new AccessTokenResponse(
                jwtService.createAccessToken(claims.subject()), "Bearer", jwtService.accessTokenExpiresInSeconds());
    }
}
