package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.bi.go_to.controller.place.request.PlaceSearchRequest;
import kr.bi.go_to.controller.place.response.PlaceSearchResponse;
import kr.bi.go_to.enums.SwaggerTag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.ModelAttribute;

@Tag(name = SwaggerTag.PLACE_NAME, description = SwaggerTag.PLACE_DESCRIPTION)
public interface PlaceApiSpec {

    @Operation(
            tags = SwaggerTag.PLACE_NAME,
            summary = "현재 위치 기반 장소 탐색",
            description = "현재 위치에서 가까운 순으로 장소를 조회하며 카테고리 필터 정보를 함께 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "장소 탐색 성공",
                content = @Content(schema = @Schema(implementation = PlaceSearchResponse.class))),
        @ApiResponse(responseCode = "400", description = "요청 파라미터 검증 실패", content = @Content)
    })
    PlaceSearchResponse search(@Valid @ParameterObject @ModelAttribute PlaceSearchRequest request);
}
