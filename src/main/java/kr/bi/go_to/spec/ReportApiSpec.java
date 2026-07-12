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
            summary = "Create facility status report",
            description =
                    "Creates a facility status report. If the reported node is a checkpoint, the response includes PDR calibration coordinates confirmed at report creation time.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Report created",
                content = @Content(schema = @Schema(implementation = ReportResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Authentication required",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Facility node not found",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ReportResponse create(AuthenticatedMember member, @Valid @RequestBody CreateReportRequest request);
}
