package kr.bi.go_to.model.facilityNode;

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
import kr.bi.go_to.model.floorMap.FloorMap;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "facility_node")
public class FacilityNode extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_map_id", nullable = false)
    private FloorMap floorMap;

    private String targetFeatureId;

    @Column(nullable = false)
    private String nodeType;

    private String name;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point geojsonPoint;

    @Column(nullable = false)
    private Boolean isCheckpoint = false;

    private Integer snapRadius;

    @Column(name = "created_by")
    private Long createdBy;

    protected FacilityNode() {}

    public FacilityNode(
            FloorMap floorMap,
            String targetFeatureId,
            String nodeType,
            String name,
            Point geojsonPoint,
            Boolean isCheckpoint,
            Integer snapRadius,
            Long createdBy) {
        this.floorMap = floorMap;
        this.targetFeatureId = targetFeatureId;
        this.nodeType = nodeType;
        this.name = name;
        this.geojsonPoint = geojsonPoint;
        this.isCheckpoint = isCheckpoint;
        this.snapRadius = snapRadius;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public FloorMap getFloorMap() {
        return floorMap;
    }

    public String getTargetFeatureId() {
        return targetFeatureId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getName() {
        return name;
    }

    public Point getGeojsonPoint() {
        return geojsonPoint;
    }

    public Boolean getIsCheckpoint() {
        return isCheckpoint;
    }

    public Integer getSnapRadius() {
        return snapRadius;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
