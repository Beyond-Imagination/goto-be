package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.bi.go_to.controller.admin.response.FacilityNodeResponse;
import kr.bi.go_to.enums.SwaggerTag;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = SwaggerTag.PLACE_NAME, description = SwaggerTag.PLACE_DESCRIPTION)
public interface PlaceFacilityNodeApiSpec {

    @Operation(
            tags = SwaggerTag.PLACE_NAME,
            summary = "시설 노드 목록 조회",
            description = "특정 장소의 특정 층에 등록된 시설 노드(엘리베이터, 화장실 등) 목록을 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "노드 목록 조회 성공",
                content =
                        @Content(array = @ArraySchema(schema = @Schema(implementation = FacilityNodeResponse.class)))),
        @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
        @ApiResponse(responseCode = "404", description = "도면 없음", content = @Content)
    })
    List<FacilityNodeResponse> listNodes(@PathVariable Long placeId, @PathVariable Integer floor);
}
