package kr.bi.go_to.service;

import kr.bi.go_to.controller.admin.request.CreateFacilityNodeRequest;
import kr.bi.go_to.controller.admin.response.FacilityNodeResponse;
import kr.bi.go_to.model.facilityNode.FacilityNode;
import kr.bi.go_to.model.facilityNode.FacilityNodeRepository;
import kr.bi.go_to.model.floorMap.FloorMap;
import kr.bi.go_to.model.floorMap.FloorMapRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FacilityNodeService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final FloorMapRepository floorMapRepository;
    private final FacilityNodeRepository facilityNodeRepository;

    public FacilityNodeService(FloorMapRepository floorMapRepository, FacilityNodeRepository facilityNodeRepository) {
        this.floorMapRepository = floorMapRepository;
        this.facilityNodeRepository = facilityNodeRepository;
    }

    @Transactional
    public FacilityNodeResponse createNode(Long placeId, Integer floor, CreateFacilityNodeRequest request) {
        FloorMap floorMap = floorMapRepository
                .findByPlaceIdAndFloorLevel(placeId, floor)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Floor map not found: placeId=%d, floor=%d".formatted(placeId, floor)));

        // Coordinate 순서: (경도, 위도)
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(request.lng(), request.lat()));

        FacilityNode node = new FacilityNode(
                floorMap,
                request.targetFeatureId(),
                request.nodeType(),
                request.name(),
                point,
                request.isCheckpoint() != null ? request.isCheckpoint() : false,
                request.snapRadius(),
                null); // TODO: Place 엔티티 연동 후 인증된 사용자 ID로 교체

        return FacilityNodeResponse.from(facilityNodeRepository.save(node));
    }
}
