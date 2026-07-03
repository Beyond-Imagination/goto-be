package kr.bi.go_to.model.help;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 현장에서 사용자가 도움을 요청하고 도우미가 매칭된 내역을 보관하는 로깅 엔티티
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "help_matching_logs")
public class HelpMatchingLog extends BaseAuditEntity {

    /**
     * 도움 매칭 내역 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 실시간 도움 요청 엔티티와 연결되는 식별자
     */
    @Column(name = "help_request_id", unique = true)
    private UUID helpRequestId;

    /**
     * 도움이 발생한 장소 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    /**
     * 도움을 요청한 사용자 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    /**
     * 요청을 수락하여 도움을 제공한 사용자 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helper_id")
    private Member helper;

    /**
     * 도움 요청 당시 파악된 요청자의 마지막 위치 (예: 층수, 위경도 등이 담긴 스냅샷 JSON)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_known_location", columnDefinition = "jsonb")
    private LastKnownLocation lastKnownLocation;

    /**
     * 도움 요청 발생 일시
     */
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    /**
     * 도움이 완료되거나 매칭이 성사되어 상황이 종료된 일시
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    public HelpMatchingLog(HelpRequest helpRequest) {
        this.helpRequestId = helpRequest.getId();
        this.place = helpRequest.getPlace();
        this.requester = helpRequest.getRequester();
        this.helper = helpRequest.getHelper();
        this.lastKnownLocation = new LastKnownLocation(
                helpRequest.getFloorLevel(),
                helpRequest.getLatitude().doubleValue(),
                helpRequest.getLongitude().doubleValue(),
                null,
                LocationProvider.USER_REQUEST,
                new HashMap<>());
        this.requestedAt = helpRequest.getRequestedAt();
        this.completedAt = null;
    }

    public void complete(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
