package kr.bi.go_to.service;

import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Member getOrCreateUser(String nickname) {
        String normalizedNickname = nickname.trim();
        return memberRepository
                .findByNickname(normalizedNickname)
                .orElseGet(() -> memberRepository.save(new Member(Role.USER, normalizedNickname)));
    }

    @Transactional(readOnly = true)
    public Member getUser(Long memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member not found"));
    }
}
