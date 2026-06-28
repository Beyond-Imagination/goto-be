package kr.bi.go_to.model.floorMap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.bi.go_to.model.common.BaseAuditEntity;

@Entity
@Table(name = "floor_map")
public class FloorMap extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: Place 엔티티 생성 후 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "place_id") private Place place; 로 교체
    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(nullable = false)
    private Integer floorLevel;

    @Column(columnDefinition = "jsonb")
    private String geojsonData;

    @Column(name = "created_by")
    private Long createdBy;

    protected FloorMap() {}

    public FloorMap(Long placeId, Integer floorLevel, String geojsonData, Long createdBy) {
        this.placeId = placeId;
        this.floorLevel = floorLevel;
        this.geojsonData = geojsonData;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public Integer getFloorLevel() {
        return floorLevel;
    }

    public String getGeojsonData() {
        return geojsonData;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
