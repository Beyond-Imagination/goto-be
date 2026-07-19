package kr.bi.go_to.service;

import java.util.List;
import kr.bi.go_to.controller.admin.request.CreateFacilityNodeRequest;
import kr.bi.go_to.controller.admin.response.FacilityNodeResponse;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.map.FacilityNode;
import kr.bi.go_to.model.map.FloorGeoJson;
import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.FloorMapRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacilityNodeService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final FloorMapRepository floorMapRepository;
    private final FacilityNodeRepository facilityNodeRepository;
    private final MemberService memberService;

    public FacilityNodeService(
            FloorMapRepository floorMapRepository,
            FacilityNodeRepository facilityNodeRepository,
            MemberService memberService) {
        this.floorMapRepository = floorMapRepository;
        this.facilityNodeRepository = facilityNodeRepository;
        this.memberService = memberService;
    }

    @Transactional
    public FacilityNodeResponse createNode(
            Long memberId, Long placeId, Integer floor, CreateFacilityNodeRequest request) {
        FloorMap floorMap = floorMapRepository
                .findByPlace_IdAndFloorLevel(placeId, floor)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLOOR_MAP_NOT_FOUND));

        validateTargetFeatureId(floorMap.getGeojsonData(), request.targetFeatureId());

        Member createdBy = memberService.getUser(memberId);

        // Coordinate 순서: (경도, 위도)
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(request.lng(), request.lat()));

        FacilityNode node = FacilityNode.builder()
                .floorMap(floorMap)
                .targetFeatureId(request.targetFeatureId())
                .nodeType(request.nodeType())
                .name(request.name())
                .geojsonPoint(point)
                .isCheckpoint(request.isCheckpoint() != null ? request.isCheckpoint() : false)
                .snapRadius(request.snapRadius())
                .locationDescription(request.locationDescription())
                .createdBy(createdBy)
                .build();

        return FacilityNodeResponse.from(facilityNodeRepository.save(node));
    }

    @Transactional(readOnly = true)
    public List<FacilityNodeResponse> listNodes(Long placeId, Integer floor) {
        FloorMap floorMap = floorMapRepository
                .findByPlace_IdAndFloorLevel(placeId, floor)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLOOR_MAP_NOT_FOUND));

        return facilityNodeRepository.findByFloorMap_Id(floorMap.getId()).stream()
                .map(FacilityNodeResponse::from)
                .toList();
    }

    private void validateTargetFeatureId(FloorGeoJson geojsonData, String targetFeatureId) {
        if (targetFeatureId == null || targetFeatureId.isBlank()) {
            return;
        }

        boolean exists = geojsonData != null
                && geojsonData.getFeatures() != null
                && geojsonData.getFeatures().stream()
                        .anyMatch(feature -> feature.getProperties() != null
                                && targetFeatureId.equals(
                                        feature.getProperties().get("node_id")));

        if (!exists) {
            throw new BusinessException(ErrorCode.TARGET_FEATURE_NOT_FOUND);
        }
    }
}
