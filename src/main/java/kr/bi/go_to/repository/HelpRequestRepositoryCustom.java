package kr.bi.go_to.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpRequest;
import kr.bi.go_to.model.help.HelpRequestStatus;

public interface HelpRequestRepositoryCustom {

    List<HelpRequest> findNearbyOpenRequests(
            Long memberId,
            HelpRequestStatus status,
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusMeters,
            Instant now);

    Optional<HelpRequest> findByIdForUpdate(UUID id);

    int expireRequestedRequests(HelpRequestStatus requestedStatus, HelpRequestStatus expiredStatus, Instant now);
}
