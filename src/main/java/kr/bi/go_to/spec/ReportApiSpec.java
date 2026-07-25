package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.report.request.CreateReportRequest;
import kr.bi.go_to.controller.report.response.ReportResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = SwaggerTag.REPORT_NAME, description = SwaggerTag.REPORT_DESCRIPTION)
public interface ReportApiSpec {

    @Operation(
            tags = SwaggerTag.REPORT_NAME,
            summary = "시설물 상태 제보 생성",
            description =
                    "시설물 노드 상태 제보를 생성합니다. 제보한 노드가 체크포인트이면 제보 생성 시점에 확인된 PDR 보정 좌표를 함께 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "제보 생성 성공",
                content = @Content(schema = @Schema(implementation = ReportResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "시설물 노드를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ReportResponse create(AuthenticatedMember member, @Valid @RequestBody CreateReportRequest request);
}
