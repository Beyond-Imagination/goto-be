package kr.bi.go_to.model.map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.member.Member;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * 실내 지도상에 위치한 엘리베이터, 화장실 등의 개별 편의시설 노드를 관리하는 엔티티
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "facility_nodes")
public class FacilityNode extends BaseAuditEntity {

    /**
     * 시설물 노드 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 시설물이 속한 실내 층별 지도 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_map_id", nullable = false)
    private FloorMap floorMap;

    /**
     * GeoJSON 데이터 내 특정 피처와 논리적으로 매핑하기 위한 문자열 고유 식별자
     */
    @Column(name = "target_feature_id", length = 100)
    private String targetFeatureId;

    /**
     * 시설물의 종류 (예: ELEVATOR, TOILET 등)
     */
    @Column(name = "node_type", nullable = false, length = 50)
    private String nodeType;

    /**
     * 시설물 이름 또는 별칭
     */
    @Column(length = 255)
    private String name;

    /**
     * 실내에서의 절대적인 Point 좌표값
     */
    @Column(name = "geojson_point", columnDefinition = "geometry(Point,4326)")
    private Point geojsonPoint;

    /**
     * PDR(보행자 데드레코닝) 센서 이동 궤적을 캘리브레이션할 수 있는 보정 영점 여부
     */
    @Column(name = "is_checkpoint", nullable = false)
    @Builder.Default
    private boolean isCheckpoint = false;

    /**
     * 사용자가 해당 시설물 근처로 접근했을 때 보정을 허용할 오차 반경 (단위: m)
     */
    @Column(name = "snap_radius")
    private Integer snapRadius;

    /**
     * 사람이 읽을 수 있는 위치 설명 (예: 신라역사관 동쪽 복도 끝, 로비에서 30m)
     */
    @Column(name = "location_description", length = 255)
    private String locationDescription;

    /**
     * 시설물 노드 정보를 등록한 사용자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;
}
