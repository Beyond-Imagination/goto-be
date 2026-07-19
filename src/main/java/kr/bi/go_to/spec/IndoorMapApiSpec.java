package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.model.map.FloorGeoJson;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = SwaggerTag.PLACE_NAME, description = SwaggerTag.PLACE_DESCRIPTION)
public interface IndoorMapApiSpec {

    @Operation(
            tags = SwaggerTag.PLACE_NAME,
            summary = "실내 도면 조회",
            description = "특정 장소의 특정 층 도면을 GeoJSON FeatureCollection으로 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "도면 조회 성공",
                content = @Content(schema = @Schema(implementation = FloorGeoJson.class))),
        @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
        @ApiResponse(responseCode = "404", description = "도면 없음", content = @Content)
    })
    ResponseEntity<FloorGeoJson> getIndoorMap(@PathVariable Long placeId, @PathVariable Integer floor);
}
