package kr.bi.go_to.service;

import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.member.OAuthUser;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.OAuthUserRepository;
import kr.bi.go_to.service.oauth.OAuthIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthRegistrationService {

    private final MemberRepository memberRepository;
    private final OAuthUserRepository oauthUserRepository;
    private final TermsService termsService;

    public OAuthRegistrationService(
            MemberRepository memberRepository, OAuthUserRepository oauthUserRepository, TermsService termsService) {
        this.memberRepository = memberRepository;
        this.oauthUserRepository = oauthUserRepository;
        this.termsService = termsService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member register(
            OAuthIdentity identity,
            String nickname,
            long agreementMask,
            MemberPreferences preferences,
            String clientIp,
            String userAgent) {
        Member member = memberRepository.save(new Member(Role.USER, nickname, agreementMask, preferences));
        oauthUserRepository.saveAndFlush(new OAuthUser(member, identity.provider(), identity.providerId()));
        termsService.recordUserAgreements(member.getId(), agreementMask, clientIp, userAgent);
        return member;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member register(OAuthIdentity identity, String nickname, long agreementMask, MemberPreferences preferences) {
        return register(identity, nickname, agreementMask, preferences, null, null);
    }
}
