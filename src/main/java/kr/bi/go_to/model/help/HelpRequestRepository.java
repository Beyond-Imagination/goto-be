package kr.bi.go_to.model.help;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID> {

    List<HelpRequest> findByStatusAndExpiresAtAfterOrderByRequestedAtDesc(HelpRequestStatus status, Instant now);

    List<HelpRequest> findByRequesterIdOrHelperIdOrderByRequestedAtDesc(Long requesterId, Long helperId);
}
