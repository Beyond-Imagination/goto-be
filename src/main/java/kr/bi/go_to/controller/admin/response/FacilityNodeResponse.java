package kr.bi.go_to.controller.admin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.bi.go_to.model.map.FacilityNode;

@Schema(name = "FacilityNodeResponse", description = "시설 노드 응답")
public record FacilityNodeResponse(
        @Schema(description = "노드 ID") Long id,
        @Schema(description = "층 도면 ID") Long floorMapId,
        @Schema(description = "GeoJSON feature의 node_id") String targetFeatureId,
        @Schema(description = "노드 유형") String nodeType,
        @Schema(description = "노드 이름") String name,
        @Schema(description = "위도") Double lat,
        @Schema(description = "경도") Double lng,
        @Schema(description = "PDR 영점 조절 가능 여부") Boolean isCheckpoint,
        @Schema(description = "보정 허용 반경(m)") Integer snapRadius,
        @Schema(description = "사람이 읽을 수 있는 위치 설명") String locationDescription) {

    public static FacilityNodeResponse from(FacilityNode node) {
        return new FacilityNodeResponse(
                node.getId(),
                node.getFloorMap().getId(),
                node.getTargetFeatureId(),
                node.getNodeType(),
                node.getName(),
                node.getGeojsonPoint().getY(),
                node.getGeojsonPoint().getX(),
                node.isCheckpoint(),
                node.getSnapRadius(),
                node.getLocationDescription());
    }
}
