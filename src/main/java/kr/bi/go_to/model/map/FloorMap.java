package kr.bi.go_to.model.map;

import jakarta.persistence.*;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 특정 장소의 실내 층별 지도(도면)를 관리하는 엔티티
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "floor_maps")
public class FloorMap extends BaseAuditEntity {

    /**
     * 도면 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 도면이 속한 장소 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /**
     * 도면의 층수 (예: 1, 2, -1(지하), 0(실외) 등)
     */
    @Column(name = "floor_level", nullable = false)
    private Integer floorLevel;

    /**
     * 실내 공간을 렌더링하기 위한 Mapbox 용도의 벡터 폴리곤 GeoJSON 데이터
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geojson_data", columnDefinition = "jsonb")
    private FloorGeoJson geojsonData;

    /**
     * 도면 데이터를 최초로 생성하거나 업로드한 작성자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;
}
