package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.place.request.PlaceSearchRequest;
import kr.bi.go_to.controller.place.response.PlaceSearchResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

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

    @Operation(
            tags = SwaggerTag.PLACE_NAME,
            summary = "장소 저장",
            description = "장소를 저장(즐겨찾기)합니다. 이미 저장돼 있으면 그대로 성공 처리합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "저장 성공"),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "장소를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    void save(AuthenticatedMember member, @PathVariable Long id);

    @Operation(
            tags = SwaggerTag.PLACE_NAME,
            summary = "장소 저장 취소",
            description = "저장(즐겨찾기)한 장소를 취소합니다. 저장돼 있지 않아도 그대로 성공 처리합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "취소 성공"),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    void unsave(AuthenticatedMember member, @PathVariable Long id);
}
