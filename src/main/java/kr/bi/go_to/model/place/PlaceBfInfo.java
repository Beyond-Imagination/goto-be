package kr.bi.go_to.model.place;

import jakarta.persistence.*;
import java.time.Instant;
import kr.bi.go_to.model.common.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 장소의 무장애(Barrier-Free) 편의시설 상세 정보를 관리하는 엔티티
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "place_bf_info")
public class PlaceBfInfo extends BaseAuditEntity {

    /**
     * 무장애 정보를 소유한 장소 ID (PK 겸 FK)
     */
    @Id
    @Column(name = "place_id")
    private Long placeId;

    /**
     * 무장애 정보가 속한 장소 엔티티 (1:1 관계)
     */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "place_id")
    private Place place;

    /**
     * 수유실, 점자블록, 휠체어 대여 여부 등 100여 개의 편의시설 메타데이터를 구조화한 JSON
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bf_details", columnDefinition = "jsonb")
    private PlaceBfDetails bfDetails;

    /**
     * 해당 무장애 정보가 외부 API로부터 동기화된 최근 일시
     */
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    public PlaceBfInfo(Place place, PlaceBfDetails bfDetails) {
        this.place = place;
        this.bfDetails = bfDetails;
        this.lastSyncedAt = Instant.now();
    }
}
