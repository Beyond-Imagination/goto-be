package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.placereport.request.CreatePlaceStateReportRequest;
import kr.bi.go_to.controller.placereport.response.PlaceStateReportResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;

@Tag(name = SwaggerTag.PLACE_STATE_REPORT_NAME, description = SwaggerTag.PLACE_STATE_REPORT_DESCRIPTION)
public interface PlaceStateReportApiSpec {

    @Operation(
            tags = SwaggerTag.PLACE_STATE_REPORT_NAME,
            summary = "장소 상태 제보 생성",
            description = "공원·건물·화장실 등 장소 전반의 이용 경험을 제보합니다. "
                    + "장애물 제보와 달리 좌표가 아니라 장소 ID에 붙으며, 외부 동기화 무장애 정보(place_bf_info)를 덮어쓰지 않습니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "생성 성공",
                content = @Content(schema = @Schema(implementation = PlaceStateReportResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 파라미터 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "장소를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    PlaceStateReportResponse create(AuthenticatedMember member, CreatePlaceStateReportRequest request);

    @Operation(tags = SwaggerTag.PLACE_STATE_REPORT_NAME, summary = "장소 상태 제보 상세 조회")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = PlaceStateReportResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "제보를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    PlaceStateReportResponse get(Long id);

    @Operation(
            tags = SwaggerTag.PLACE_STATE_REPORT_NAME,
            summary = "장소별 상태 제보 목록 조회",
            description = "해당 장소의 최근 제보를 최신순으로 반환합니다. 페이지네이션은 아직 도입되지 않았습니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content =
                        @Content(
                                array =
                                        @ArraySchema(
                                                schema = @Schema(implementation = PlaceStateReportResponse.class)))),
        @ApiResponse(
                responseCode = "404",
                description = "장소를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    List<PlaceStateReportResponse> findByPlace(Long placeId);
}
