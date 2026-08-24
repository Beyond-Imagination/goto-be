package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.obstaclereport.request.CreateObstacleReportRequest;
import kr.bi.go_to.controller.obstaclereport.request.ObstacleReportClusterRequest;
import kr.bi.go_to.controller.obstaclereport.request.UpdateObstacleReportStatusRequest;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportClusterResponse;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;

@Tag(name = SwaggerTag.OBSTACLE_REPORT_NAME, description = SwaggerTag.OBSTACLE_REPORT_DESCRIPTION)
public interface ObstacleReportApiSpec {

    @Operation(
            tags = SwaggerTag.OBSTACLE_REPORT_NAME,
            summary = "장애물 제보 생성",
            description = "실외 보행 경로상의 장애물(보도 파손, 공사, 높은 턱 등)을 제보합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "생성 성공",
                content = @Content(schema = @Schema(implementation = ObstacleReportResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 파라미터 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ObstacleReportResponse create(AuthenticatedMember member, CreateObstacleReportRequest request);

    @Operation(tags = SwaggerTag.OBSTACLE_REPORT_NAME, summary = "장애물 제보 상세 조회")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = ObstacleReportResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "제보를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ObstacleReportResponse get(Long id);

    @Operation(
            tags = SwaggerTag.OBSTACLE_REPORT_NAME,
            summary = "장애물 제보 상태 변경",
            description = "\"아직 있어요\"(STILL_PRESENT) 확인을 기록하거나 \"해결됐어요\"(RESOLVED)로 종료 처리합니다. "
                    + "이미 RESOLVED된 제보는 재오픈되지 않으며, 두 액션 모두 409를 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "변경 성공",
                content = @Content(schema = @Schema(implementation = ObstacleReportResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "제보를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이미 해결 처리된 제보",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ObstacleReportResponse updateStatus(AuthenticatedMember member, Long id, UpdateObstacleReportStatusRequest request);

    @Operation(
            tags = SwaggerTag.OBSTACLE_REPORT_NAME,
            summary = "장애물 제보 클러스터 조회",
            description = "bbox/zoom 내 제보를 격자 기반으로 묶어 클러스터별 요약을 반환합니다. mobilityType으로 필터링할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = ObstacleReportClusterResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 파라미터 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    List<ObstacleReportClusterResponse> getClusters(ObstacleReportClusterRequest request);
}
