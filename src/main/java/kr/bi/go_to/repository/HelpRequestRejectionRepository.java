package kr.bi.go_to.repository;

import java.util.UUID;

import kr.bi.go_to.model.help.HelpRequestRejection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRejectionRepository extends JpaRepository<HelpRequestRejection, Long> {

    boolean existsByHelpRequestIdAndMemberId(UUID helpRequestId, Long memberId);
}
