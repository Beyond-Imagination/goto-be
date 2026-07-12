package kr.bi.go_to.controller.report.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.service.report.model.ReportData;

@Schema(name = "ReportResponse", description = "Facility status report response")
public record ReportResponse(
        @Schema(description = "Report ID", example = "1") Long id,
        @Schema(description = "Reported facility node ID", example = "1") Long nodeId,
        @Schema(description = "Report issue type", example = "BROKEN") String issueType,
        @Schema(description = "Report details", nullable = true) String description,
        @Schema(description = "Report creation time") Instant createdAt,
        @Schema(description = "PDR calibration data. Null when the node is not a checkpoint.", nullable = true)
                CalibrationResponse calibration) {

    public static ReportResponse from(ReportData report) {
        return new ReportResponse(
                report.id(),
                report.node().id(),
                report.issueType(),
                report.description(),
                report.createdAt(),
                CalibrationResponse.from(report));
    }
}
