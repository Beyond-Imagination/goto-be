package kr.bi.go_to.service;

import kr.bi.go_to.enums.Role;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
