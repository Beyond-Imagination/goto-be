package kr.bi.go_to.controller.report.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateReportRequest", description = "Facility status report creation request")
public record CreateReportRequest(
        @Schema(description = "Reported facility node ID", example = "1") @NotNull @Positive Long nodeId,
        @Schema(description = "Report issue type", example = "BROKEN") @NotBlank @Size(max = 50) String issueType,
        @Schema(description = "Report details", nullable = true, example = "The elevator is stopped.") @Size(max = 1000)
                String description) {}
