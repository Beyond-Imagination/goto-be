package kr.bi.go_to.model.help;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRejectionRepository extends JpaRepository<HelpRequestRejection, HelpRequestRejectionId> {

    boolean existsByHelpRequestIdAndMemberId(UUID helpRequestId, Long memberId);
}
