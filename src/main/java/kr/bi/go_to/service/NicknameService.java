package kr.bi.go_to.service;

import java.util.regex.Pattern;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NicknameService {

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9]{2,12}$");

    private final MemberRepository memberRepository;

    public NicknameService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(String rawNickname) {
        return !memberRepository.existsByNickname(normalizeAndValidate(rawNickname));
    }

    public String normalizeAndValidate(String rawNickname) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();

        if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return nickname;
    }
}
