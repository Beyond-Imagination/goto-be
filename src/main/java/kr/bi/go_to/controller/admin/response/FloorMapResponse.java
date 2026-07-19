package kr.bi.go_to.controller.admin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.bi.go_to.model.map.FloorGeoJson;
import kr.bi.go_to.model.map.FloorMap;

@Schema(name = "FloorMapResponse", description = "도면 응답")
public record FloorMapResponse(
        @Schema(description = "도면 ID") Long id,
        @Schema(description = "장소 ID") Long placeId,
        @Schema(description = "층수") Integer floorLevel,
        @Schema(description = "GeoJSON FeatureCollection") FloorGeoJson geojsonData,
        @Schema(description = "최초 작성자 ID") Long createdBy) {

    public static FloorMapResponse from(FloorMap floorMap) {
        return new FloorMapResponse(
                floorMap.getId(),
                floorMap.getPlace().getId(),
                floorMap.getFloorLevel(),
                floorMap.getGeojsonData(),
                floorMap.getCreatedBy() != null ? floorMap.getCreatedBy().getId() : null);
    }
}
