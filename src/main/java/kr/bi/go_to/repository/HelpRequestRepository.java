package kr.bi.go_to.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpRequest;
import kr.bi.go_to.model.help.HelpRequestStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID>, HelpRequestRepositoryCustom {

    List<HelpRequest> findByRequesterIdOrHelperIdOrderByRequestedAtDesc(Long requesterId, Long helperId);

    /**
     * 푸시를 더 넓은 대상으로 확대해야 하는 요청들.
     *
     * <p>아직 아무도 수락하지 않았고(REQUESTED), 만료 전이며, 올라온 지 확대 대기 시간이 지났고,
     * 아직 확대한 적이 없는 요청만 고른다.
     */
    @Query(
            """
            select h from HelpRequest h
            where h.status = :status
              and h.pushEscalatedAt is null
              and h.requestedAt <= :escalateBefore
              and h.expiresAt > :now
            order by h.requestedAt
            """)
    List<HelpRequest> findForPushEscalation(
            @Param("status") HelpRequestStatus status,
            @Param("escalateBefore") Instant escalateBefore,
            @Param("now") Instant now,
            Limit limit);
}
