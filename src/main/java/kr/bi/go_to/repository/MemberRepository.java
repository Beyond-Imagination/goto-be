package kr.bi.go_to.repository;

import java.util.Optional;
import kr.bi.go_to.model.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByNickname(String nickname);
}
