package kr.bi.go_to.service;

import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Member getUser(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
