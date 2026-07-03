package kr.bi.go_to.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import kr.bi.go_to.model.help.HelpRequest;
import kr.bi.go_to.model.help.HelpRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID> {

    @Query(
            value =
                    """
                    SELECT h.*
                    FROM help_requests h
                    WHERE h.status = :status
                      AND h.expires_at > :now
                      AND h.requester_id <> :memberId
                      AND NOT EXISTS (
                          SELECT 1
                          FROM help_request_rejections r
                          WHERE r.help_request_id = h.id
                            AND r.member_id = :memberId
                      )
                      AND ST_DWithin(
                          ST_SetSRID(ST_MakePoint(h.longitude::double precision, h.latitude::double precision), 4326)::geography,
                          ST_SetSRID(ST_MakePoint(CAST(:longitude AS double precision), CAST(:latitude AS double precision)), 4326)::geography,
                          :radiusMeters
                      )
                    ORDER BY h.requested_at DESC
                    """,
            nativeQuery = true)
    List<HelpRequest> findNearbyOpenRequests(
            @Param("memberId") Long memberId,
            @Param("status") String status,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("now") Instant now);

    List<HelpRequest> findByRequesterIdOrHelperIdOrderByRequestedAtDesc(Long requesterId, Long helperId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE HelpRequest h
            SET h.status = :expiredStatus
            WHERE h.status = :requestedStatus
              AND h.expiresAt <= :now
            """)
    int expireRequestedRequests(
            @Param("requestedStatus") HelpRequestStatus requestedStatus,
            @Param("expiredStatus") HelpRequestStatus expiredStatus,
            @Param("now") Instant now);
}
