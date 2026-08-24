package kr.bi.go_to.model.obstaclereport;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.member.Member;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * 실외 보행 경로상의 통행 장애 지점(보도 파손, 공사, 높은 턱 등)에 대한 사용자 제보 엔티티.
 * 같은 물리적 지점에 대해 여러 사용자가 각자 독립된 제보를 남길 수 있으며, DB상에서 병합되지 않는다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "obstacle_reports")
public class ObstacleReport extends BaseAuditEntity {

    /**
     * 오래됨(STALE) 판단 임계값 — 마지막 확인으로부터 이 기간이 지나면 조회 시점에 STALE로 파생 표시한다.
     */
    private static final Duration STALE_THRESHOLD = Duration.ofDays(30);

    /**
     * 장애물 제보 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 제보를 작성한 회원 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    /**
     * 장애물의 절대 위치 (PLACE와는 연관관계를 갖지 않는다)
     */
    @Column(name = "location_point", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point locationPoint;

    /**
     * 장애물 유형
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 50)
    private ObstacleIssueType issueType;

    /**
     * 심각도
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ObstacleSeverity severity;

    /**
     * 영향받는 이동조건 유형 (선택 입력 — 홈 지도 이동조건 필터에서 클러스터를 필터링하는 데 쓰인다)
     */
    @ElementCollection
    @CollectionTable(
            name = "obstacle_report_affected_mobility_types",
            joinColumns = @JoinColumn(name = "obstacle_report_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "mobility_type", nullable = false, length = 50)
    @Builder.Default
    private Set<MobilityType> affectedMobilityTypes = new HashSet<>();

    /**
     * 이미 호스팅된 사진 URL 목록 (업로드 인프라는 이번 스코프에 포함하지 않음)
     */
    @ElementCollection
    @CollectionTable(name = "obstacle_report_photo_urls", joinColumns = @JoinColumn(name = "obstacle_report_id"))
    @OrderColumn(name = "photo_order")
    @Column(name = "photo_url", nullable = false, length = 2048)
    @Builder.Default
    private List<String> photoUrls = List.of();

    /**
     * 리포트 생명주기 상태. RESOLVED는 재오픈되지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ObstacleReportStatus status = ObstacleReportStatus.ACTIVE;

    /**
     * ObstacleReportConfirmation에 새 확인이 insert된 횟수(=확인한 서로 다른 회원 수)
     */
    @Column(name = "confirmed_count", nullable = false)
    @Builder.Default
    private int confirmedCount = 0;

    /**
     * 마지막으로 "아직 있어요" 확인이 들어온 시각 (없으면 null)
     */
    @Column(name = "last_confirmed_at")
    private Instant lastConfirmedAt;

    public void confirm(Instant now) {
        this.confirmedCount++;
        this.lastConfirmedAt = now;
    }

    public void resolve() {
        this.status = ObstacleReportStatus.RESOLVED;
    }

    public boolean isResolved() {
        return status == ObstacleReportStatus.RESOLVED;
    }

    public boolean isStale(Instant now) {
        if (isResolved()) {
            return false;
        }
        Instant freshnessReference = lastConfirmedAt != null ? lastConfirmedAt : getCreatedAt();
        return freshnessReference.plus(STALE_THRESHOLD).isBefore(now);
    }
}
