package kr.bi.go_to.support;

import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.service.JwtService;

public final class TestMemberAuthentication {

    private TestMemberAuthentication() {}

    public static String accessToken(MemberRepository memberRepository, JwtService jwtService, String nickname) {
        Member member = memberRepository.save(new Member(Role.USER, nickname));
        return jwtService.createAccessToken(member.getId().toString());
    }
}
