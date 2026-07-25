package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.admin.response.FloorMapResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.model.map.FloorGeoJson;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = SwaggerTag.ADMIN_NAME, description = SwaggerTag.ADMIN_DESCRIPTION)
public interface AdminFloorMapApiSpec {

    @Operation(
            tags = SwaggerTag.ADMIN_NAME,
            summary = "도면 등록/수정",
            description = "특정 장소의 특정 층 도면을 GeoJSON FeatureCollection으로 등록합니다. 이미 존재하면 덮어씁니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "도면 등록/수정 성공",
                content = @Content(schema = @Schema(implementation = FloorMapResponse.class))),
        @ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
        @ApiResponse(responseCode = "404", description = "장소 없음", content = @Content)
    })
    FloorMapResponse upsertFloorMap(
            AuthenticatedMember member,
            @PathVariable Long placeId,
            @PathVariable Integer floor,
            @RequestBody FloorGeoJson request);
}
