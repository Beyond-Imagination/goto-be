package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.bi.go_to.enums.SwaggerTag;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = SwaggerTag.PLACE_NAME, description = SwaggerTag.PLACE_DESCRIPTION)
public interface PlaceFloorApiSpec {

    @Operation(tags = SwaggerTag.PLACE_NAME, summary = "층 목록 조회", description = "특정 장소에 도면이 등록된 층 목록을 오름차순으로 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "층 목록 조회 성공",
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = Integer.class)))),
        @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
    })
    List<Integer> listFloors(@PathVariable Long placeId);
}
